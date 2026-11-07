// 화면 비례 확대와 실시간 흐름 확인 (움직임 켬)
const { chromium } = require('playwright');
const [file, out] = process.argv.slice(2);
const st = { step: 9, name: '송유성', groups: [0, 1], expType: 2, sides: [1, 1, 0, 1], choice: 4, goals: [4, 19, 1], picks: [4, 19], self: { 3: 2, 11: 2 }, checks: [0, 1, 3, 5], plus: [3], minus: [4] };
(async () => {
  const b = await chromium.launch();
  for (const [w, h] of [[1366, 768], [1920, 1080], [2560, 1440], [1280, 720]]) {
    const p = await b.newPage({ viewport: { width: w, height: h }, reducedMotion: 'no-preference' });
    const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'assert' && errs.push(m.text()));
    await p.goto(require('url').pathToFileURL(require('path').resolve(file)).href);
    await p.evaluate(s => localStorage.setItem('experience-ai-map-v1', JSON.stringify(s)), st);
    await p.reload(); await p.waitForTimeout(4500); // 결론 재생이 끝나고 실시간 흐름이 시작될 때까지
    const m = await p.evaluate(() => {
      const mp = document.querySelector('.map-page');
      return { z: getComputedStyle(document.documentElement).getPropertyValue('--z'), docScrollY: document.documentElement.scrollHeight - innerHeight, xover: document.documentElement.scrollWidth - innerWidth,
        mapScroll: mp.scrollHeight - mp.clientHeight, appBottom: Math.round(document.querySelector('.app').getBoundingClientRect().bottom), strands: document.querySelectorAll('#wires path.strand.on').length };
    });
    // 흐르는 점이 선 위에 있는지 표본 측정
    let dots = 0, onWire = 0;
    for (let t = 0; t < 25; t++) {
      const r = await p.evaluate(() => [...document.querySelectorAll('#wires circle:not(.col)')].map(d => { const pt = new DOMPoint(+d.getAttribute('cx'), +d.getAttribute('cy'));
        return [...document.querySelectorAll('#wires path:not(.strand)')].some(q => { q.style.strokeWidth = '6px'; const hit = q.isPointInStroke(pt); q.style.strokeWidth = ''; return hit; }); }));
      dots += r.length; onWire += r.filter(Boolean).length;
      if (t === 8) await p.screenshot({ path: `${out}/live_${w}.png` });
      await p.waitForTimeout(120);
    }
    console.log(`${w}x${h}`, JSON.stringify(m), `| 흐르는 점 ${dots}개 중 선 위 ${onWire}`, errs.length ? errs : '');
    await p.close();
  }
  await b.close();
})();
