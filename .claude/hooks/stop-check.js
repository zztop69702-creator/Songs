#!/usr/bin/env node
// Stop 훅 (작업을 마치려 할 때): 이번에 바뀐 파일(git 기준)을 다시 검사하고, devos.json 의 빠른 테스트를 해당될 때만 한 번 돌린다.
// 문제가 있으면 종료 코드 2 로 한 번 알린다. 이미 Stop 훅 때문에 이어진 응답(stop_hook_active)이면 바로 끝내 무한 반복을 막는다.
// 같은 변경 내용으로 통과한 테스트는 다시 돌리지 않는다(.claude/backups/.stop-cache.json).
'use strict';
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const { readStdinJson, config, checkFile, projectDir, run } = require('./lib');

if (!__dirname.startsWith(path.join(projectDir(), '.claude')) && fs.existsSync(path.join(projectDir(), '.claude', 'hooks', 'stop-check.js'))) process.exit(0);
const input = readStdinJson();
if (input.stop_hook_active) process.exit(0);

const git = run('git', ['-C', projectDir(), 'status', '--porcelain', '--untracked-files=all']);
if (!git.ok) process.exit(0); // git 저장소가 아니면 조용히 끝
const changed = git.out.split('\n').map(l => l.slice(3).trim().replace(/^"|"$/g, '')).filter(Boolean)
  .map(f => (f.includes(' -> ') ? f.split(' -> ')[1] : f)).filter(f => !f.startsWith('.claude/backups/'));
if (!changed.length) process.exit(0);

const messages = [];
// 바뀐 파일이 아주 많은 프로젝트에서도 빨리 끝나도록 앞의 30개만 검사한다
for (const f of changed.slice(0, 30)) {
  const problems = checkFile(path.join(projectDir(), f));
  if (problems.length) messages.push(`${f}\n- ${problems.join('\n- ')}`);
}

const cacheFile = path.join(projectDir(), '.claude', 'backups', '.stop-cache.json');
let cache = {};
try { cache = JSON.parse(fs.readFileSync(cacheFile, 'utf8')); } catch { /* 처음 */ }
for (const t of config().stopTests || []) {
  const hit = changed.filter(f => f.startsWith(t.when));
  if (!hit.length) continue;
  const hash = crypto.createHash('sha1');
  hit.forEach(f => { try { hash.update(f); hash.update(fs.readFileSync(path.join(projectDir(), f))); } catch { /* 지워진 파일 */ } });
  const key = `${t.when}|${hash.digest('hex')}`;
  if (cache[t.when] === key) continue; // 같은 내용으로 이미 통과
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'devos-stop-'));
  const cmd = t.run.replace(/\{tmp\}/g, tmp);
  const r = run(process.platform === 'win32' ? 'cmd' : 'sh', process.platform === 'win32' ? ['/c', cmd] : ['-c', cmd], { cwd: projectDir(), timeoutSec: t.timeoutSec || 120 });
  const failed = !r.ok || (t.failPattern && new RegExp(t.failPattern).test(r.out));
  try { fs.rmSync(tmp, { recursive: true, force: true }); } catch { /* 임시 폴더 정리 실패 무시 */ }
  if (failed) messages.push(`[빠른 테스트 실패] ${t.name}\n${r.out.split('\n').slice(-12).join('\n')}`);
  else { cache[t.when] = key; }
}
try { fs.mkdirSync(path.dirname(cacheFile), { recursive: true }); fs.writeFileSync(cacheFile, JSON.stringify(cache)); } catch { /* 캐시 실패 무시 */ }

if (messages.length) {
  process.stderr.write(`[작업 종료 검사] 바뀐 파일 ${changed.length}개 중 문제가 있습니다. 고치거나, 고치지 못하면 '검증하지 못함/실패'로 사용자에게 보고하세요.\n\n${messages.join('\n\n')}\n`);
  process.exit(2);
}
process.exit(0);
