// 경력(1~2개) × 경험 유형 5 × 대표 성향 조합에서 추천(★·☆)으로 닿는 전문가 수
const { chromium } = require('playwright');
(async () => { const b = await chromium.launch(); const p = await b.newPage(); const errs = [];
  p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'assert' && errs.push(m.text()));
  await p.goto(require('url').pathToFileURL(require('path').resolve(process.argv[2])).href); await p.waitForTimeout(300);
  const r = await p.evaluate(() => {
    const reach = new Set(); const sidesSet = [[null, null, null, null], [0, 0, 0, 0], [1, 1, 1, 1], [0, 1, 0, 1], [1, 0, 1, 0]];
    GROUPS.forEach((_, a) => GROUPS.forEach((_, c) => [0, 1, 2, 3, 4].forEach(e => sidesSet.forEach(sd => {
      const l = a === c ? [a] : [a, c]; S.groups = l; S.expType = e; S.sides = sd;
      [...ranked(), ...extraRecs()].forEach(j => reach.add(j));
    }))));
    const un = JOBS.map((_, j) => j).filter(j => !reach.has(j)).map(j => NUM[j] + '.' + SHORT[j]);
    S.groups = [2]; S.expType = 4; S.sides = [0, 0, 0, 0]; const sales = extraRecs().map(j => NUM[j] + SHORT[j]);
    return { reachable: reach.size, unreachable: un, salesExample: sales };
  });
  console.log(JSON.stringify(r)); console.log('ERRORS', errs); await b.close(); })();
