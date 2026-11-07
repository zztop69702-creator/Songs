// 1쪽 두 단계 고르기 검사: 1단계 하고 싶은 것(최대 4) · 2단계 하고 있는 것(최대 4), 5개째 막힘, 단계 간 옮기기,
// 두 열 출발점과 빛줄기, 2쪽 3단계와 같은 목록인지, 화면 넘침. 사용법: node twostage.js <app.html> <결과 폴더>
const { chromium } = require('playwright');
const path = require('path');
const [file, out] = process.argv.slice(2);
const NUMS = n => `.job:has(small:text-is("${n}"))`;
(async () => {
  const b = await chromium.launch();
  const errs = [];
  for (const vp of [{ width: 1366, height: 900 }, { width: 1920, height: 1080 }, { width: 390, height: 844 }]) {
    const p = await b.newPage({ viewport: vp, deviceScaleFactor: 2 });
    p.on('pageerror', e => errs.push(`${vp.width} ${e.message}`)); p.on('console', m => m.type() === 'assert' && errs.push(`${vp.width} ${m.text()}`));
    await p.goto(require('url').pathToFileURL(path.resolve(file)).href);
    await p.evaluate(() => localStorage.clear()); await p.reload(); await p.waitForTimeout(400);
    if (vp.width < 900) await p.click('#pageMap').catch(() => {});
    const st = () => p.evaluate(() => ({ want: S.picks.map(j => NUM[j]).join(','), have: S.have.map(j => NUM[j]).join(','), wantN: document.getElementById('wantN').textContent, haveN: document.getElementById('haveN').textContent,
      cols: document.querySelectorAll('#sources .src-col').length, srcWant: document.querySelectorAll('#sources .src:not(.gh)').length, srcHave: document.querySelectorAll('#sources .src.gh').length,
      ghWires: document.querySelectorAll('#wires path.pair.gh').length, ghOn: document.querySelectorAll('#wires path.pair.gh.on').length, xover: document.documentElement.scrollWidth - innerWidth,
      srcOverflow: [...document.querySelectorAll('#sources .src')].some(e => e.getBoundingClientRect().right > document.getElementById('stage').getBoundingClientRect().right) }));
    for (const n of [1, 2, 3, 6]) { await p.click(NUMS(n)); await p.waitForTimeout(150); }
    await p.click(NUMS(7)); await p.waitForTimeout(150);
    const a = await st(); const msgA = (await p.innerText('#info')).split('\n')[0];
    await p.click('#pickMode [data-mode="have"]');
    for (const n of [5, 10, 3, 16]) { await p.click(NUMS(n)); await p.waitForTimeout(150); }
    await p.click(NUMS(20)); await p.waitForTimeout(1500);
    const h = await st(); const msgH = (await p.innerText('#info')).split('\n')[0];
    if (vp.width !== 390) await p.locator('.map-page').screenshot({ path: `${out}/two_${vp.width}.png` });
    else await p.screenshot({ path: `${out}/two_390.png`, fullPage: true });
    // 2쪽 3단계와 같은 목록인지
    await p.evaluate(() => { S.step = 2; S.groups = [0]; save(); }); await p.reload(); await p.waitForTimeout(400);
    const page2 = await p.evaluate(() => [...document.querySelectorAll('[data-have][aria-pressed="true"]')].map(b => NUM[b.dataset.have]).join(','));
    // 1쪽 지도만 비우기 뒤에도 단계 버튼 개수가 맞는지
    console.log(`${vp.width}x${vp.height} | 1단계 ${a.want} (${a.wantN}) 5개째: "${msgA}" | 2단계 ${h.have} (${h.haveN}) 1단계는 ${h.want} (3번은 옮겨짐) 5개째: "${msgH}" | 두 열 ${h.cols} · 출발점 ${h.srcWant}+${h.srcHave} · 2단계 빛줄기 ${h.ghWires}(켜짐 ${h.ghOn}) | 2쪽 3단계 ${page2} | 넘침 ${h.xover} 상자넘침 ${h.srcOverflow}`);
    await p.close();
  }
  console.log('ERRORS', errs);
  await b.close();
})();
