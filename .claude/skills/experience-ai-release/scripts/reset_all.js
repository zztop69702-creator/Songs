// 지도 비우기 확인 창 검사: 창이 뜨는지, 취소·Esc 는 아무것도 바꾸지 않는지,
// '1쪽 지도만 비우기'는 2쪽 답을 남기는지, '모두 지우기'는 1쪽 지도와 2쪽 답을 모두 지우고 새로고침 뒤에도 비어 있는지.
// 사용법: node reset_all.js <app.html> <결과 폴더>
const { chromium } = require('playwright');
const path = require('path');
const [file, out] = process.argv.slice(2);
const STATE = { step: 9, name: '홍길동(예시)', groups: [1, 2], expType: 1, sides: [0, 1, 0, 0], choice: 6, goals: [6, 26, 24], picks: [1, 23], self: { 0: 4, 3: 2 }, checks: [0, 1, 3], plus: [0], minus: [1], exp: '점검 순서를 바꿨습니다.' };
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1366, height: 900 }, deviceScaleFactor: 2 });
  const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'assert' && errs.push(m.text()));
  await p.goto(require('url').pathToFileURL(path.resolve(file)).href);
  const load = async () => { await p.evaluate(s => localStorage.setItem('experience-ai-map-v1', JSON.stringify(s)), STATE); await p.reload(); await p.waitForTimeout(500); };
  const snap = () => p.evaluate(() => {
    const d = JSON.parse(localStorage.getItem('experience-ai-map-v1'));
    return { 단계: d.step, 이름: d.name, 경력: d.groups.length, 목표: d.goals.length, 관심: d.picks.length, 자가진단: Object.keys(d.self).length, 돌발: d.minus.length,
      빛줄기: document.querySelectorAll('#wires path.on').length, 출발점: document.querySelectorAll('#sources .src').length, 창열림: document.getElementById('resetDlg').open,
      '2쪽': document.getElementById('stepText')?.textContent.trim() };
  });
  await load();
  const before = await snap();
  console.log('① 처음:', JSON.stringify(before));
  await p.click('#mapReset'); await p.waitForTimeout(200);
  console.log('② 지도 비우기 → 확인 창 열림:', (await snap()).창열림, '| 기본 초점:', await p.evaluate(() => document.activeElement.textContent));
  await p.screenshot({ path: `${out}/확인창.png` });
  await p.click('#resetDlg button[value="cancel"]'); await p.waitForTimeout(200);
  const c = await snap();
  console.log('③ 취소 → 그대로:', JSON.stringify(c) === JSON.stringify(before));
  await p.click('#mapReset'); await p.keyboard.press('Escape'); await p.waitForTimeout(200);
  console.log('④ Esc → 그대로:', JSON.stringify(await snap()) === JSON.stringify(before));
  await p.click('#mapReset'); await p.click('#resetMap'); await p.waitForTimeout(500);
  const m = await snap();
  console.log('⑤ 1쪽만 비우기 → 관심', m.관심, '· 출발점', m.출발점, '· 2쪽 목표', m.목표, '· 단계', m.단계, '(목표·단계는 그대로여야 함)');
  await load();
  await p.click('#mapReset'); await p.click('#resetAll'); await p.waitForTimeout(800);
  const a = await snap();
  console.log('⑥ 모두 지우기:', JSON.stringify(a));
  await p.screenshot({ path: `${out}/모두지운뒤.png` });
  await p.reload(); await p.waitForTimeout(500);
  const r = await snap();
  console.log('⑦ 새로고침 뒤에도 비어 있음:', r.단계 === 0 && !r.이름 && !r.경력 && !r.목표 && !r.관심 && !r.자가진단 && !r.돌발 && r.출발점 === 0);
  console.log('ERRORS', errs);
  await b.close();
})();
