// 배포 파일(dist/online.html)로 사용자가 보낸 화면 그대로 재현: 실제 클릭 + 시간 측정 + 단계별 캡처
const { chromium } = require('playwright');
const [file, out] = process.argv.slice(2);
(async () => {
  const b = await chromium.launch();
  for (const motion of ['no-preference', 'reduce']) {
    const ctx = await b.newContext({ viewport: { width: 1366, height: 768 }, reducedMotion: motion, deviceScaleFactor: motion === 'reduce' ? 1 : 2 });
    const p = await ctx.newPage();
    const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'assert' && errs.push(m.text()));
    await p.goto(require('url').pathToFileURL(require('path').resolve(file)).href);
    // 사용자 상태: 10단계, 목표 ① 16 시설운영개선, 돌발 '업무 시간 부족', 관심 전문가 없음
    await p.evaluate(s => { localStorage.clear(); localStorage.setItem('experience-ai-map-v1', JSON.stringify(s)); }, { step: 9, name: '송유성', groups: [0, 1], expType: 2, sides: [1, 1, 0, 1], choice: 4, goals: [4], picks: [], self: {}, checks: [0, 1, 3, 5], plus: [], minus: [4] });
    await p.reload(); await p.waitForTimeout(3800);
    const snap = () => p.evaluate(() => ({
      출발점: [...document.querySelectorAll('.src b')].map(x => x.textContent.trim()).join(',') || '없음',
      빛줄기: document.querySelectorAll('#wires path.pair.on').length,
      찬칸: [...document.querySelectorAll('#stage .skill i')].filter(i => parseFloat(i.style.width) > 0).length,
      '표의①': [...document.querySelectorAll('.job em')].some(e => /①/.test(e.textContent)),
      허브: document.getElementById('hubText').textContent,
      문구: document.getElementById('dream').innerText.replace(/\n/g, ' '),
      점x: [...document.querySelectorAll('#wires circle:not(.col)')].map(c => Math.round(+c.getAttribute('cx'))).slice(0, 4).join(','),
      버튼: [...document.querySelectorAll('#mapHead .actions button')].filter(x => !x.hidden).map(x => x.textContent).join(' / ')
    }));
    const tag = motion === 'reduce' ? '[움직임 줄이기 PC]' : '[보통 PC]';
    const s0 = await snap(); await p.waitForTimeout(400); const s0b = await snap();
    console.log(tag, '① 처음:', JSON.stringify(s0), '| 0.4초 뒤 점x', s0b.점x || '없음');
    if (motion !== 'reduce') await p.screenshot({ path: `${out}/검증1_처음.png` });
    await p.click('#mapReset'); await p.click('#resetMap'); await p.waitForTimeout(500); // 확인 창에서 '1쪽 지도만 비우기'
    const s1 = await snap();
    console.log(tag, '② 지도 비우기:', JSON.stringify(s1));
    if (motion !== 'reduce') await p.screenshot({ path: `${out}/검증2_지도비우기.png` });
    await p.click('.job[data-job="17"]'); await p.waitForTimeout(1800);
    const s2 = await snap();
    console.log(tag, '③ 17 설비데이터 클릭(탐색):', JSON.stringify(s2));
    if (motion !== 'reduce') await p.screenshot({ path: `${out}/검증3_탐색.png` });
    const goal = (await p.innerText('#summary')).match(/목표\s*\n?([^\n]*)\n?([^\n]*)/);
    console.log(tag, '   2쪽 요약 목표(그대로여야 함):', (goal?.[1] + ' ' + (goal?.[2] || '')).trim().slice(0, 60));
    await p.click('#mapGoals'); await p.waitForTimeout(2600);
    const s3 = await snap();
    console.log(tag, '④ 내 목표 보기:', JSON.stringify(s3));
    if (motion !== 'reduce') await p.screenshot({ path: `${out}/검증4_내목표보기.png` });
    if (motion === 'reduce') {
      await p.click('#motionBtn'); await p.waitForTimeout(3000);
      const a = await snap(); await p.waitForTimeout(400); const c = await snap();
      console.log(tag, '⑤ 움직임 켜기 후:', a.버튼, '| 점x', a.점x || '없음', '→', c.점x || '없음');
    }
    console.log(tag, '오류:', errs.length ? errs : '0건');
    await ctx.close();
  }
  await b.close();
})();
