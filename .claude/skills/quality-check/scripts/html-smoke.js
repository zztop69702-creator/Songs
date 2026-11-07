// 범용 HTML 앱 빠른 검사: PC(1366×768)·휴대폰(390×844)으로 열어
//  - 페이지 오류(pageerror), 콘솔 오류, 앱 안 자동검사(console.assert) 실패를 모으고
//  - 가로 넘침(px)을 재고, 화면 캡처를 남긴다.
// 사용법: node html-smoke.js <html 파일> <결과 폴더>   (Playwright 필요: require('playwright'))
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const [file, out = '.'] = process.argv.slice(2);
if (!file) { console.error('사용법: node html-smoke.js <html 파일> <결과 폴더>'); process.exit(1); }
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch();
  let bad = 0;
  for (const [w, h] of [[1366, 768], [390, 844]]) {
    const p = await b.newPage({ viewport: { width: w, height: h } });
    const errs = [];
    p.on('pageerror', e => errs.push('페이지 오류: ' + e.message));
    p.on('console', m => { if (m.type() === 'error') errs.push('콘솔 오류: ' + m.text()); if (m.type() === 'assert') errs.push('자동검사 실패: ' + m.text()); });
    await p.goto('file://' + path.resolve(file));
    await p.waitForTimeout(1500);
    const over = await p.evaluate(() => document.documentElement.scrollWidth - innerWidth);
    const shot = path.join(out, `smoke_${w}.png`);
    await p.screenshot({ path: shot, fullPage: false });
    console.log(`${w}x${h} 가로 넘침 ${over}px | 캡처 ${shot} | 오류 ${errs.length ? errs.join(' / ') : '0건'}`);
    bad += errs.length + (over > 0 ? 1 : 0);
    await p.close();
  }
  await b.close();
  process.exit(bad ? 1 : 0);
})();
