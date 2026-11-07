// 해 본 역할(2단계 하고 있는 것, 최대 4) 검사: 전후 숫자(5단계 후보·학습 비중), 9개째 막힘, 화면 캡처. 사용법: node have_roles.js <app.html> <결과 폴더>
const { chromium } = require('playwright');
const path = require('path');
const [file, out] = process.argv.slice(2);
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1366, height: 900 }, deviceScaleFactor: 2 });
  const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'assert' && errs.push(m.text()));
  await p.goto(require('url').pathToFileURL(path.resolve(file)).href);
  const haveNames = ['일정·공정관리', '품질·안전점검', '문서작성코치', '업무자동화']; // 2단계 최대 4
  const base = { step: 2, name: '홍길동(예시)', groups: [0, 3], expType: 2, sides: [1, 0, 0, 0], picks: [], goals: [], self: {}, checks: [0, 1, 3, 4], plus: [], minus: [] };
  const run = async have => {
    await p.evaluate(([s, names]) => { s.have = names.map(n => SHORT.indexOf(n)); localStorage.setItem('experience-ai-map-v1', JSON.stringify(s)); }, [base, have]);
    await p.reload(); await p.waitForTimeout(400);
    return p.evaluate(() => {
      const lab = j => { const o = ranked(); return o.includes(j) ? '★추천' : extraRecs().includes(j) ? '☆추가' : nextRecs(recsNow()).includes(j) ? '→다음 행보' : '1쪽'; };
      const cand = candidates();
      const goal = cand[0];
      const rows = personalRows(goal);
      return { cand: cand.map(j => `${NUM[j]} ${SHORT[j]}(${lab(j)})`).join(', '), goal: `${NUM[goal]} ${SHORT[goal]}`, rows: rows.slice(0, 5).map(r => `${SKILLS[r.k]} ${r.pct}%`).join(', '), n: rows.length };
    });
  };
  const before = await run([]);
  const after = await run(haveNames);
  console.log('해 본 역할 없음 | 5단계 후보:', before.cand);
  console.log('                | 1순위', before.goal, '배울 것', before.n, '개 · 상위5:', before.rows);
  console.log('해 본 역할 4개   | 5단계 후보:', after.cand);
  console.log('                | 1순위', after.goal, '배울 것', after.n, '개 · 상위5:', after.rows);
  // 같은 목표로 비교(목표를 고정하면 학습 비중만 달라진다)
  const same = await p.evaluate(g => { const s = S.have; const a = personalRows(g); S.have = []; const c = personalRows(g); S.have = s; const f = r => r.slice(0, 6).map(x => `${SKILLS[x.k]} ${x.pct}%`).join(', '); return { before: f(c), after: f(a) }; }, await p.evaluate(() => candidates()[0]));
  console.log('같은 목표 고정 | 전:', same.before);
  console.log('              | 후:', same.after);
  // 캡처: 2쪽 3단계(경험) 해 본 역할 선택
  await p.setViewportSize({ width: 1366, height: 2300 }); await p.waitForTimeout(300);
  await p.locator('.flow-page').screenshot({ path: `${out}/have_1_2쪽선택.png` });
  await p.setViewportSize({ width: 1366, height: 900 });
  // 5단계 후보
  await p.evaluate(() => { S.step = 4; save(); }); await p.reload(); await p.waitForTimeout(400);
  await p.click('#jobChoices [data-cand] >> nth=0'); await p.waitForTimeout(1500);
  await p.locator('.flow-page').screenshot({ path: `${out}/have_2_5단계후보.png` });
  await p.locator('.map-page').screenshot({ path: `${out}/have_3_1쪽지도.png` });
  // 9개째는 막힘
  await p.evaluate(() => { S.step = 2; S.have = [0, 1, 2, 3]; save(); }); await p.reload(); await p.waitForTimeout(300);
  await p.click('[data-have="8"]'); const msg = await p.textContent('#haveCount'); const cnt = await p.evaluate(() => S.have.length);
  console.log('5개째 누름 →', cnt, '개 유지 ·', msg);
  console.log('ERRORS', errs);
  await b.close();
})();
