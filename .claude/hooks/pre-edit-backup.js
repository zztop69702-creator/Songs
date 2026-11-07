#!/usr/bin/env node
// PreToolUse 훅 (Edit|Write|MultiEdit 직전): 이미 있는 파일을 하루에 한 번, 파일별로 .claude/backups/날짜/ 에 사본으로 남긴다.
// 수정을 막지 않는다(항상 0으로 끝남). git 이 기본 백업이고, 이것은 커밋 전 작업을 위한 보조 백업이다.
'use strict';
const fs = require('fs');
const path = require('path');
const { readStdinJson, projectDir, rel } = require('./lib');

if (!__dirname.startsWith(path.join(projectDir(), '.claude')) && fs.existsSync(path.join(projectDir(), '.claude', 'hooks', 'pre-edit-backup.js'))) process.exit(0);
try {
  const ti = (readStdinJson().tool_input) || {};
  const file = ti.file_path || ti.notebook_path;
  if (!file) process.exit(0);
  const abs = path.resolve(projectDir(), file);
  const r = rel(abs);
  if (r.startsWith('..') || r.startsWith('.claude/backups') || !fs.existsSync(abs)) process.exit(0);
  if (fs.statSync(abs).size > 10 * 1024 * 1024) process.exit(0); // 10MB 넘는 파일은 백업하지 않음
  const day = new Date().toISOString().slice(0, 10).replace(/-/g, '');
  const dest = path.join(projectDir(), '.claude', 'backups', day, r);
  if (fs.existsSync(dest)) process.exit(0); // 오늘 이미 백업함 → 첫 상태를 보존
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(abs, dest);
} catch { /* 백업 실패가 작업을 막지 않게 한다 */ }
process.exit(0);
