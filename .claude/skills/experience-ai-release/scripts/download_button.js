// 온라인 페이지의 'PC 설치 파일 받기' 버튼: window.claude.use('downloads') 를 흉내 내 저장 호출을 확인한다
const { chromium } = require('playwright');
const zlib = require('zlib');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1366, height: 768 } });
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addInitScript(() => {
    window.__saved = null;
    window.claude = { use: name => Promise.resolve(name === 'downloads' ? { save: async req => { window.__saved = { filename: req.filename, size: req.data.length, head: Array.from(req.data.slice(0, 4)) }; return { status: 'saved' }; } } : null) };
  });
  await p.goto('file://' + process.argv[2]); await p.waitForTimeout(600);
  console.log('버튼 보임:', await p.isVisible('#installBtn'));
  await p.screenshot({ path: process.argv[3] });
  await p.click('#installBtn'); await p.waitForTimeout(300);
  const s = await p.evaluate(() => window.__saved);
  console.log('저장 요청:', s && s.filename, s && s.size, 'bytes, 머리글', s && s.head.map(x => x.toString(16)).join(' '), '(50 4b 3 4 = ZIP)');
  console.log('안내:', await p.textContent('#installMsg'));
  console.log('ERRORS', errs);
  // 설치된 앱(파일 없음)에서는 버튼이 숨는지
  const q = await b.newPage(); await q.addInitScript(() => { window.claude = { use: () => Promise.resolve({ save: async () => ({}) }) }; });
  await q.goto('file://' + process.argv[4]); await q.waitForTimeout(600);
  console.log('설치된 앱에서 버튼 보임:', await q.isVisible('#installBtn'));
  await b.close();
})();
