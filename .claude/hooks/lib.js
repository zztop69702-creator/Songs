// 운영체계 훅 공용 함수: 설정 읽기, 파일 한 개 문법·한글 깨짐 검사
// 무거운 검사는 하지 않는다(파일 하나, 몇 초 안). Windows·Linux 공통(node 만 사용).
'use strict';
const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawnSync } = require('child_process');

const projectDir = () => process.env.CLAUDE_PROJECT_DIR || process.cwd();

function readStdinJson() {
  try { return JSON.parse(fs.readFileSync(0, 'utf8') || '{}'); } catch { return {}; }
}

function config() {
  const base = { checkExtensions: ['.js', '.mjs', '.cjs', '.html', '.htm', '.json', '.py'], protectedFiles: ['CLAUDE.md', '.claude/settings.json', 'package.json'], stopTests: [] };
  try { return { ...base, ...JSON.parse(fs.readFileSync(path.join(projectDir(), '.claude', 'devos.json'), 'utf8')) }; } catch { return base; }
}

const rel = f => path.relative(projectDir(), path.resolve(projectDir(), f)).split(path.sep).join('/');

function run(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, { encoding: 'utf8', timeout: (opts.timeoutSec || 20) * 1000, shell: false, ...opts });
  return { ok: r.status === 0, out: `${r.stdout || ''}${r.stderr || ''}`.trim(), missing: r.error && r.error.code === 'ENOENT' };
}

function nodeCheck(code, ext) {
  const tmp = path.join(os.tmpdir(), `devos-check-${process.pid}-${Date.now()}${ext}`);
  fs.writeFileSync(tmp, code);
  const r = run(process.execPath, ['--check', tmp]);
  try { fs.unlinkSync(tmp); } catch { /* 임시 파일 정리 실패는 무시 */ }
  return r.ok ? null : r.out.split('\n').filter(l => !l.includes('devos-check-')).slice(0, 6).join('\n');
}

// 파일 하나를 검사해 문제 목록(문자열 배열)을 돌려준다. 문제가 없으면 []
function checkFile(file) {
  const problems = [];
  const ext = path.extname(file).toLowerCase();
  if (!config().checkExtensions.includes(ext) || !fs.existsSync(file)) return problems;
  const stat = fs.statSync(file);
  if (stat.size > 5 * 1024 * 1024) return problems; // 5MB 넘는 파일은 건너뛴다(생성물일 가능성)
  const text = fs.readFileSync(file, 'utf8');
  // 한글 깨짐: 잘못된 인코딩으로 저장되면 대체 문자 U+FFFD 가 생긴다
  const bad = (text.match(/\uFFFD/g) || []).length;
  if (bad) problems.push(`한글 깨짐 의심: 대체 문자(U+FFFD) ${bad}개 — 파일을 UTF-8로 저장했는지 확인`);
  if (['.js', '.mjs', '.cjs'].includes(ext)) {
    const e = nodeCheck(text, ext === '.cjs' ? '.cjs' : ext === '.mjs' ? '.mjs' : '.js');
    if (e) problems.push(`JavaScript 문법 오류:\n${e}`);
  } else if (ext === '.html' || ext === '.htm') {
    // 외부 파일이 아닌 실행용 <script> 블록만 검사 (json·text·template 는 제외)
    const re = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi; let m, i = 0;
    while ((m = re.exec(text))) {
      const attrs = m[1];
      i++;
      if (/\bsrc\s*=/.test(attrs)) continue;
      const type = (attrs.match(/\btype\s*=\s*["']?([^"'\s>]+)/i) || [])[1] || '';
      if (type && !/^(text\/javascript|module|application\/javascript)$/i.test(type)) continue;
      if (!m[2].trim()) continue;
      const line = text.slice(0, m.index).split('\n').length;
      const e = nodeCheck(m[2], type === 'module' ? '.mjs' : '.js');
      if (e) problems.push(`HTML 안 ${i}번째 <script>(파일 ${line}행부터) 문법 오류:\n${e}`);
    }
  } else if (ext === '.json') {
    try { JSON.parse(text.replace(/^﻿/, '')); } catch (e) { problems.push(`JSON 형식 오류: ${e.message}`); }
  } else if (ext === '.py') {
    let r = run('python3', ['-m', 'py_compile', file]);
    if (r.missing) r = run('python', ['-m', 'py_compile', file]);
    if (!r.missing && !r.ok) problems.push(`Python 문법 오류:\n${r.out.split('\n').slice(-4).join('\n')}`);
  }
  // 린트: 프로젝트에 eslint 가 설치돼 있을 때만, 이 파일 하나만
  if (['.js', '.mjs', '.cjs'].includes(ext)) {
    const eslint = path.join(projectDir(), 'node_modules', '.bin', process.platform === 'win32' ? 'eslint.cmd' : 'eslint');
    if (fs.existsSync(eslint)) { const r = run(eslint, [file], { timeoutSec: 30, shell: process.platform === 'win32' }); if (!r.ok) problems.push(`ESLint:\n${r.out.split('\n').slice(0, 10).join('\n')}`); }
  }
  return problems;
}

module.exports = { readStdinJson, config, checkFile, projectDir, rel, run };
