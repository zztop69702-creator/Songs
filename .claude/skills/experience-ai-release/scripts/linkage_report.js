// 앱 데이터에서 '역량 연결표 AI 추론 검증 보고서'를 만든다 (사람이 표를 다시 칠 필요 없이 앱이 원본)
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage(); const errs = [];
  p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'assert' && errs.push(m.text()));
  await p.goto('file://' + process.argv[2]); await p.waitForTimeout(300);
  const d = await p.evaluate(() => {
    const reach = new Set(); const sidesSet = [[null, null, null, null], [0, 0, 0, 0], [1, 1, 1, 1], [0, 1, 0, 1], [1, 0, 1, 0]];
    GROUPS.forEach((_, a) => GROUPS.forEach((_, c) => [0, 1, 2, 3, 4].forEach(e => sidesSet.forEach(sd => {
      S.groups = a === c ? [a] : [a, c]; S.expType = e; S.sides = sd; [...ranked(), ...extraRecs()].forEach(j => reach.add(j));
    }))));
    const order = CATS.flatMap(c => c.j);
    return {
      skills: SKILLS, verify: VERIFY,
      rows: order.map(j => ({ num: NUM[j], cat: CATS[CAT[j]].n, name: JOBS[j].n, s: JOBS[j].s, plan: j < 7, reach: reach.has(j) })),
      perSkill: SKILLS.map((_, k) => JOBS.filter(x => x.s.includes(k)).length),
      linkMin: Math.min(...JOBS.map(x => x.s.length)), linkMax: Math.max(...JOBS.map(x => x.s.length)), reach: reach.size
    };
  });
  const L = [];
  L.push('# 역량 연결표 · AI 추론 검증 보고서', '');
  L.push('경험곱하기AI v1.8 · 전문가 30명 × AI 역량 14개. 이 보고서는 앱 데이터(`src/app.html`)에서 자동으로 만든다.', '');
  L.push('## 검증 기준', '');
  L.push('1. **필수성**: 그 전문가의 핵심 결과물을 이 역량 없이는 제 품질로 만들 수 없는가');
  L.push('2. **학습 분산**: 전문가마다 연결 3~6개 (넘치면 학습 시간이 흩어진다)');
  L.push('3. **도달 가능성**: 역량마다 전문가 5명 이상, 전문가는 경력 추천(★·☆)으로 닿을 수 있는가');
  L.push('4. **기획안 보존**: 앞 7명(기획안 62쪽)의 기존 8개 역량 연결은 바꾸지 않는다', '');
  L.push('## 자동 검사 결과 (앱 안에서 매번 실행)', '');
  L.push(`- 전문가별 연결 수: ${d.linkMin}~${d.linkMax}개 (기준 3~6) ${d.linkMin >= 3 && d.linkMax <= 6 ? '통과' : '위반'}`);
  L.push(`- 역량별 전문가 수: 최소 ${Math.min(...d.perSkill)}명 (기준 5명 이상) ${Math.min(...d.perSkill) >= 5 ? '통과' : '위반'}`);
  L.push(`- 경력 추천(★ 3명 + ☆ 추가 2명)으로 닿는 전문가: ${d.reach} / 30명 (나머지는 1쪽에서 직접 선택)`);
  L.push(`- 앱 자동 검사 오류: ${errs.length ? errs.join(', ') : '0건'}`, '');
  L.push('## 이번 검증으로 바꾼 것과 근거', '', '| 대상 | 변경 | 근거 |', '|---|---|---|');
  d.verify.forEach(([a, c, r]) => L.push(`| ${a} | ${c} | ${r} |`));
  L.push('', '## 역량별 연결 전문가 수', '', '| ' + d.skills.join(' | ') + ' |', '|' + d.skills.map(() => '---').join('|') + '|', '| ' + d.perSkill.join(' | ') + ' |', '');
  L.push('## 전체 연결표 (● 연결, 번호는 1쪽 표 번호)', '');
  L.push('| 번호 | 분야 | 전문가 | ' + d.skills.join(' | ') + ' | 연결 수 | 추천 도달 |');
  L.push('|---|---|---|' + d.skills.map(() => ':-:').join('|') + '|:-:|:-:|');
  d.rows.forEach(r => L.push(`| ${r.num} | ${r.cat} | ${r.name}${r.plan ? ' (기획안)' : ''} | ` + d.skills.map((_, k) => r.s.includes(k) ? '●' : '').join(' | ') + ` | ${r.s.length} | ${r.reach ? 'O' : '1쪽 선택'} |`));
  L.push('', '## 한계와 다음 검증', '');
  L.push('- 이 검증은 **논리 일관성**을 보장한다. **현장 적합성**은 시범 사용자 3~5명의 결과(막힌 단계, 결과카드 납득 여부)로 확인해야 한다.');
  L.push('- 같은 AI가 만들고 검증했으므로 같은 편향이 남을 수 있다. 다른 AI 모델로 같은 기준을 적용해 교차 검증하면 좋다.');
  L.push('- 저작권 등록 기준 등 사실관계는 추론으로 확정하지 않으며 공식 기관 확인이 필요하다.');
  fs.writeFileSync(process.argv[3], L.join('\n') + '\n');
  console.log('report rows', d.rows.length, 'errs', errs);
  await b.close();
})();
