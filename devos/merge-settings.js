#!/usr/bin/env node
// 사용자 settings.json 에 운영체계 훅을 '추가만' 한다. 기존 설정·훅은 지우거나 바꾸지 않는다.
// 사용법: node merge-settings.js <settings.json 경로> <훅 폴더 경로> [--dry-run]
//  - 파일이 있으면 먼저 settings.json.bak-날짜시각 으로 백업한다
//  - 같은 명령의 훅이 이미 있으면 건너뛴다(여러 번 실행해도 중복되지 않음)
//  - 기존 파일이 JSON 형식이 아니면 아무것도 바꾸지 않고 멈춘다
'use strict';
const fs = require('fs');
const path = require('path');
const [settingsPath, hooksDir, flag] = process.argv.slice(2);
if (!settingsPath || !hooksDir) { console.error('사용법: node merge-settings.js <settings.json> <훅 폴더> [--dry-run]'); process.exit(1); }
const dry = flag === '--dry-run';

let settings = {};
if (fs.existsSync(settingsPath)) {
  try { settings = JSON.parse(fs.readFileSync(settingsPath, 'utf8').replace(/^﻿/, '')); }
  catch (e) { console.error(`기존 settings.json 을 읽을 수 없어 멈춥니다(바꾸지 않음): ${e.message}`); process.exit(1); }
}
const cmd = f => `node "${path.join(hooksDir, f).replace(/\\/g, '/')}"`;
const wanted = [
  { event: 'PreToolUse', matcher: 'Edit|Write|MultiEdit', file: 'pre-edit-backup.js', timeout: 15 },
  { event: 'PostToolUse', matcher: 'Edit|Write|MultiEdit|NotebookEdit', file: 'check-file.js', timeout: 60 },
  { event: 'Stop', matcher: null, file: 'stop-check.js', timeout: 200 }
];
settings.hooks = settings.hooks || {};
const report = [];
for (const w of wanted) {
  const list = settings.hooks[w.event] = settings.hooks[w.event] || [];
  const c = cmd(w.file);
  if (list.some(g => (g.hooks || []).some(h => h.command === c))) { report.push(`건너뜀(이미 있음): ${w.event} → ${w.file}`); continue; }
  const group = { hooks: [{ type: 'command', command: c, timeout: w.timeout }] };
  if (w.matcher) group.matcher = w.matcher;
  list.push(group);
  report.push(`추가: ${w.event}${w.matcher ? ` (${w.matcher})` : ''} → ${w.file}`);
}
if (dry) { report.forEach(r => console.log('[미리보기] ' + r)); process.exit(0); }
// 추가할 것이 없으면 파일을 건드리지 않는다(백업도 만들지 않음)
if (!report.some(r => r.startsWith('추가'))) { report.forEach(r => console.log(r)); console.log('바꿀 것이 없어 settings.json 을 그대로 두었습니다.'); process.exit(0); }
if (fs.existsSync(settingsPath)) {
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace('T', '-').slice(0, 15);
  let bak = `${settingsPath}.bak-${stamp}`;
  for (let i = 2; fs.existsSync(bak); i++) bak = `${settingsPath}.bak-${stamp}-${i}`; // 기존 백업을 덮어쓰지 않는다
  fs.copyFileSync(settingsPath, bak);
  report.unshift(`백업: ${bak}`);
}
fs.mkdirSync(path.dirname(settingsPath), { recursive: true });
fs.writeFileSync(settingsPath, JSON.stringify(settings, null, 2) + '\n');
report.forEach(r => console.log(r));
