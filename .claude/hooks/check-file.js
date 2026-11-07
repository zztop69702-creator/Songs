#!/usr/bin/env node
// PostToolUse 훅 (Edit|Write|MultiEdit|NotebookEdit 직후): 방금 바꾼 파일 하나만 검사한다.
//  - 문법 오류·한글 깨짐·(있으면) ESLint → 종료 코드 2 로 Claude 에게 알려 바로 고치게 한다
//  - 중요 설정 파일이 바뀌면 → 종료 코드 2 로 '변경 이유와 백업을 보고하라'고 알린다
//  - 문제 없으면 조용히 0
'use strict';
const fs = require('fs');
const path = require('path');
const { readStdinJson, config, checkFile, projectDir, rel } = require('./lib');

// 사용자 폴더(~/.claude)에 설치된 사본은, 프로젝트에 자체 훅이 있으면 양보한다(두 번 실행 방지)
if (!__dirname.startsWith(path.join(projectDir(), '.claude')) && fs.existsSync(path.join(projectDir(), '.claude', 'hooks', 'check-file.js'))) process.exit(0);

const input = readStdinJson();
const ti = input.tool_input || {};
const file = ti.file_path || ti.notebook_path;
if (!file) process.exit(0);
const abs = path.resolve(projectDir(), file);
const messages = [];
const problems = checkFile(abs);
if (problems.length) messages.push(`[자동 검사] ${rel(abs)}\n- ${problems.join('\n- ')}`);
const r = rel(abs);
if (config().protectedFiles.some(p => r === p || r.endsWith('/' + p))) {
  messages.push(`[중요 파일 변경] ${r} — 변경 이유, 영향 범위, 백업(.claude/backups 또는 git) 여부를 사용자에게 보고하세요.`);
}
if (messages.length) { process.stderr.write(messages.join('\n\n') + '\n'); process.exit(2); }
process.exit(0);
