const { chromium } = require('playwright');
const fs = require('fs');
// 같은 입력으로 원본/수정본의 카드·지침문·판단을 뽑아 비교한다
const scen = [
  { name: 'sample', s: null },
  { name: 'c-1only', s: { step: 9, name: '', groups: [1], expType: 0, sides: [0, 1, 1, 0], choice: 4, self: { 3: 1, 5: 5, 6: 3 }, checks: [0, 1, 2, 3, 4], plus: [0, 2], minus: [2, 4], done: [3] } },
  { name: 'r-pair', s: { step: 9, name: '김', status: 'r', groups: [2, 3], expType: 4, sides: [null, null, null, null], choice: 0, self: {}, checks: [], plus: [], minus: [0], done: [] } },
  { name: 'edited', s: { step: 9, name: '박', groups: [3, 0], expType: 1, sides: [1, 1, 0, 1], choice: 1, self: { 2: 5, 3: 5 }, checks: [0, 2, 4], plus: [1], minus: [3], done: [], model: { role: '운영 개선 팀장', form: '판단기준표, FAQ' } } },
];
(async () => {
  const b = await chromium.launch(), out = {};
  for (const [tag, file] of [['orig', process.argv[2]], ['fixed', process.argv[3]]]) {
    for (const sc of scen) {
      const ctx = await b.newContext({ viewport: { width: 1366, height: 900 } }), p = await ctx.newPage();
      const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'assert' && errs.push(m.text()));
      await p.goto(require('url').pathToFileURL(require('path').resolve(file)).href);
      if (sc.s) { await p.evaluate(s => localStorage.setItem('experience-ai-map-v1', JSON.stringify(s)), sc.s); await p.reload(); }
      else await p.click('#sample');
      await p.waitForTimeout(400);
      if (await p.$('#openDoc')) await p.click('#openDoc');
      out[tag + ':' + sc.name] = [await p.innerText('#card'), await p.inputValue('#prompt'), await p.innerText('#study'), errs.join(';')].join('\n----\n');
      await ctx.close();
    }
  }
  for (const sc of scen) {
    const a = out['orig:' + sc.name].split('\n'), f = out['fixed:' + sc.name].split('\n');
    const diff = [];
    for (let i = 0; i < Math.max(a.length, f.length); i++) if (a[i] !== f[i]) diff.push(`  - ${a[i]}\n  + ${f[i]}`);
    console.log(`[${sc.name}] lines ${a.length}/${f.length}, diffs ${diff.length}`); diff.forEach(d => console.log(d));
  }
  await b.close();
})();
