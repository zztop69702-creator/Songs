// 1쪽 아래 3D '역량 도시' 검사: 보임/숨김 조건, 움직임 켬·끔, 지도 비우기, 결론 재생 중 막대 성장, 오류.
// 사용법: node city3d.js <app.html> <결과 폴더>
const { chromium } = require('playwright');
const path = require('path');
const [file, out] = process.argv.slice(2);
const STATE = { step: 8, name: '홍길동(예시)', groups: [1, 2], expType: 1, sides: [0, 1, 0, 0], choice: 6, goals: [6, 26, 24], picks: [1, 23], self: { 0: 4, 3: 2, 5: 5, 7: 2 }, checks: [0, 1, 3, 4, 5], plus: [0], minus: [] };
(async () => {
  const b = await chromium.launch();
  const errs = [];
  const sig = p => p.evaluate(() => { const c = document.getElementById('city'); if (!c.width) return 'empty'; const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data; let s = 0, lit = 0; for (let i = 0; i < d.length; i += 4) { s = (s * 31 + d[i] + d[i + 1] * 3 + d[i + 2] * 7) % 1e9; if (d[i + 3] > 0) lit++; } return s + ':' + lit; });
  const info = p => p.evaluate(() => { const d = document.getElementById('deep'), r = d.getBoundingClientRect(); return { on: d.classList.contains('on'), h: Math.round(d.clientHeight), rows: city.rows.length, hours: city.hours, docScrollY: document.documentElement.scrollHeight - innerHeight, pageScroll: (q => q.scrollHeight - q.clientHeight)(document.querySelector('.map-page')) }; });
  for (const vp of [{ width: 1320, height: 1490 }, { width: 1920, height: 1080 }, { width: 1366, height: 768 }, { width: 390, height: 844 }]) {
    const p = await b.newPage({ viewport: vp, reducedMotion: 'no-preference', deviceScaleFactor: 2 });
    p.on('pageerror', e => errs.push(`${vp.width} PAGEERROR ${e.message}`)); p.on('console', m => m.type() === 'assert' && errs.push(`${vp.width} ${m.text()}`));
    await p.goto(require('url').pathToFileURL(path.resolve(file)).href);
    await p.evaluate(s => { localStorage.setItem('experience-ai-map-v1', JSON.stringify(s)); localStorage.removeItem('experience-ai-motion'); }, STATE);
    await p.reload(); await p.waitForTimeout(400);
    await p.click('#next');
    // 결론 재생 중 막대가 0 → 목표로 자라는지 (재생 시작 직후와 끝난 뒤 높이 합)
    await p.waitForTimeout(300);
    const early = await p.evaluate(() => Object.values(city.h).reduce((a, b) => a + b, 0));
    await p.waitForTimeout(5000);
    const late = await p.evaluate(() => Object.values(city.h).reduce((a, b) => a + b, 0));
    const i1 = await info(p);
    // 시간 표시 = 결과카드 식과 같은지
    const hoursOk = await p.evaluate(() => city.rows.every(r => { const card = [...document.querySelectorAll('#summary li, #card li, .bars li')].find(li => li.querySelector('b')?.textContent === SKILLS[r.k]); return !card || card.textContent.includes((r.pct * city.hours / 100).toFixed(1)); }));
    const s1 = await sig(p); await p.waitForTimeout(400); const s2 = await sig(p);
    if (vp.width === 1320 || vp.width === 1920) await p.locator('.map-page').screenshot({ path: `${out}/city_${vp.width}.png` });
    await p.click('#motionBtn'); await p.waitForTimeout(200);
    const s3 = await sig(p); await p.waitForTimeout(500); const s4 = await sig(p);
    await p.click('#motionBtn'); await p.waitForTimeout(200);
    await p.click('#mapReset'); await p.click('#resetMap'); await p.waitForTimeout(600); // 확인 창에서 '1쪽 지도만 비우기'
    const iReset = await info(p);
    if (vp.width === 1320) await p.locator('.map-page').screenshot({ path: `${out}/city_${vp.width}_비움.png` });
    await p.click('#mapGoals'); await p.waitForTimeout(1500);
    const iBack = await info(p);
    console.log(`${vp.width}x${vp.height} | 3D영역 ${i1.on ? '보임' : '숨김'} 높이 ${i1.h}px | 막대 ${i1.rows}개 · ${i1.hours}시간 | 재생 높이합 ${early.toFixed(0)}→${late.toFixed(0)} | 시간=카드 ${hoursOk} | 움직임 켬: 바뀜 ${s1 !== s2} | 끔: 고정 ${s3 === s4} | 비우기 막대 ${iReset.rows} → 목표보기 ${iBack.rows} | 문서스크롤 ${i1.docScrollY} 1쪽스크롤 ${i1.pageScroll}`);
    await p.close();
  }
  // 움직임 줄이기(Windows 애니메이션 끔) 기본값에서는 정지
  const p = await b.newPage({ viewport: { width: 1320, height: 1490 }, reducedMotion: 'reduce' });
  p.on('pageerror', e => errs.push('reduce PAGEERROR ' + e.message));
  await p.goto(require('url').pathToFileURL(path.resolve(file)).href);
  await p.evaluate(s => { localStorage.setItem('experience-ai-map-v1', JSON.stringify(s)); localStorage.removeItem('experience-ai-motion'); }, STATE);
  await p.reload(); await p.waitForTimeout(400); await p.click('#next'); await p.waitForTimeout(5000);
  const a = await sig(p); await p.waitForTimeout(500); const c = await sig(p);
  console.log(`움직임 줄이기 PC | 그림 있음 ${a !== 'empty'} | 고정 ${a === c}`);
  console.log('ERRORS', errs);
  await b.close();
})();
