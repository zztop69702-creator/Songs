const { chromium } = require('playwright');
const [file, out] = process.argv.slice(2);
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1366, height: 768 }, reducedMotion: 'no-preference' });
  const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'assert' && errs.push(m.text()));
  await p.goto('file://' + file);
  await p.evaluate(s => localStorage.setItem('experience-ai-map-v1', JSON.stringify(s)), { step: 8, name: '송유성', groups: [1, 2], expType: 1, sides: [0, 1, 0, 0], choice: 6, goals: [6, 26, 24], picks: [1, 23], self: { 0: 4, 3: 2, 5: 5, 7: 2 }, checks: [0, 1, 3, 4, 5], plus: [0], minus: [] });
  await p.reload(); await p.waitForTimeout(500);
  await p.click('#next');
  const tl = [];
  for (let t = 0; t < 30; t++) {
    tl.push(await p.evaluate(() => ({ chips: document.querySelectorAll('#condChips i.on').length, dots: document.querySelectorAll('#wires circle').length, filled: [...document.querySelectorAll('.skill i')].filter(i => parseFloat(i.style.width) > 0).length })));
    if (t === 6) await p.screenshot({ path: `${out}/완성_1_재생중.png` });
    await p.waitForTimeout(100);
  }
  console.log('재생 경과(0.1초 간격) 켜진 조건/점/찬 칸:', tl.map(x => `${x.chips}/${x.dots}/${x.filled}`).join(' '));
  console.log('허브:', await p.textContent('#hubText'));
  console.log('요약 먼저 보임:', await p.isVisible('#summary'), '| 문서 숨김:', await p.isHidden('#fullDoc'));
  console.log('요약 내용:', (await p.innerText('#summary')).replace(/\n+/g, ' / '));
  await p.click('#openDoc');
  const n = (await p.$$('#study input')).length;
  for (let i = 0; i < n; i++) {
    await p.click(`#study label >> nth=${i}`);
    if (i === 0) console.log('1개 체크 후 허브:', await p.textContent('#hubText'), '| ✓칸:', await p.$$eval('.skill.done', e => e.length));
  }
  await p.waitForTimeout(800);
  console.log('모두 체크 후 허브:', await p.textContent('#hubText'), '| 완성 표시:', await p.evaluate(() => document.getElementById('stage').classList.contains('complete')), '| 카드:', (await p.innerText('#card')).match(/나의 정의 · 학습 완성|진행 \d+%/)?.[0]);
  await p.screenshot({ path: `${out}/완성_2_정의완성.png` });
  await p.click('#replay'); await p.waitForTimeout(150);
  console.log('요약보기 후 요약:', await p.isVisible('#summary'), '| 문서:', await p.isVisible('#fullDoc'));
  await p.waitForTimeout(2500);
  await p.screenshot({ path: `${out}/요약_먼저.png` });
  console.log('다시 보기 직후 켜진 조건:', await p.evaluate(() => document.querySelectorAll('#condChips i.on').length));
  console.log('ERRORS', errs);
  await b.close();
})();
