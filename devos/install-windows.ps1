# 송유성 개발 운영체계 V1.0 · Windows 사용자 폴더 설치 (모든 프로젝트에 적용)
# 사용법 (Warp 또는 PowerShell, 저장소 폴더에서):
#   미리보기(아무것도 바꾸지 않음):  powershell -ExecutionPolicy Bypass -File devos\install-windows.ps1 -DryRun
#   설치:                            powershell -ExecutionPolicy Bypass -File devos\install-windows.ps1
# 보호 규칙
#   - %USERPROFILE%\.claude 에 이미 있는 스킬·에이전트·훅 파일은 건너뛴다(덮어쓰지 않음)
#   - CLAUDE.md 가 이미 있으면 그대로 두고, 운영체계 규칙은 CLAUDE.devos.md 로 따로 둔다(직접 합치기)
#   - settings.json 은 백업한 뒤 훅을 '추가만' 한다(devos\merge-settings.js)
param([switch]$DryRun)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$src = Join-Path $repo '.claude'
$dst = Join-Path $HOME '.claude'
$hookDir = Join-Path $dst 'hooks\devos'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$log = New-Object System.Collections.Generic.List[string]
function Say($m) { $line = $(if ($DryRun) { "[미리보기] $m" } else { $m }); Write-Host $line; $log.Add($line) }

if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'node 를 찾을 수 없습니다. 훅 실행에 Node.js 가 필요합니다. Node.js 를 설치한 뒤 다시 실행하세요.' }
Say "원본: $src"
Say "설치 위치: $dst"

# 1) 스킬 9개 (프로젝트 전용 experience-ai-release 는 제외)
$skills = 'app-builder', 'design-system', 'quality-check', 'ventilation-expert', 'tab-report', 'estimate-builder', 'contract-docs', 'agent-blogger', 'mcp-builder'
foreach ($s in $skills) {
  $to = Join-Path $dst "skills\$s"
  if (Test-Path -LiteralPath $to) { Say "건너뜀(이미 있음): skills\$s"; continue }
  Say "설치: skills\$s"
  if (-not $DryRun) { New-Item -ItemType Directory -Force (Split-Path $to) | Out-Null; Copy-Item -Recurse -LiteralPath (Join-Path $src "skills\$s") -Destination $to }
}

# 2) 서브에이전트 4개
foreach ($f in Get-ChildItem -LiteralPath (Join-Path $src 'agents') -Filter '*.md') {
  $to = Join-Path $dst "agents\$($f.Name)"
  if (Test-Path -LiteralPath $to) { Say "건너뜀(이미 있음): agents\$($f.Name)"; continue }
  Say "설치: agents\$($f.Name)"
  if (-not $DryRun) { New-Item -ItemType Directory -Force (Split-Path $to) | Out-Null; Copy-Item -LiteralPath $f.FullName -Destination $to }
}

# 3) 훅 스크립트 (사용자 폴더 hooks\devos)
foreach ($f in 'lib.js', 'check-file.js', 'pre-edit-backup.js', 'stop-check.js') {
  $to = Join-Path $hookDir $f
  if (Test-Path -LiteralPath $to) { Say "건너뜀(이미 있음): hooks\devos\$f"; continue }
  Say "설치: hooks\devos\$f"
  if (-not $DryRun) { New-Item -ItemType Directory -Force $hookDir | Out-Null; Copy-Item -LiteralPath (Join-Path $src "hooks\$f") -Destination $to }
}

# 4) CLAUDE.md (사용자 공통 규칙)
$userMd = Join-Path $dst 'CLAUDE.md'
$rules = Join-Path $PSScriptRoot 'CLAUDE.user.md'
if (Test-Path -LiteralPath $userMd) {
  $alt = Join-Path $dst 'CLAUDE.devos.md'
  if (Test-Path -LiteralPath $alt) { Say '건너뜀(이미 있음): CLAUDE.devos.md' }
  else { Say '기존 CLAUDE.md 를 보존하고 운영체계 규칙은 CLAUDE.devos.md 로 둡니다. 두 파일을 비교해 필요한 부분만 CLAUDE.md 에 옮기세요.'; if (-not $DryRun) { Copy-Item -LiteralPath $rules -Destination $alt } }
} else {
  Say '설치: CLAUDE.md (사용자 공통 규칙)'
  if (-not $DryRun) { New-Item -ItemType Directory -Force $dst | Out-Null; Copy-Item -LiteralPath $rules -Destination $userMd }
}

# 5) settings.json 에 훅 추가 (백업 후 추가만)
$mergeArgs = @((Join-Path $PSScriptRoot 'merge-settings.js'), (Join-Path $dst 'settings.json'), $hookDir)
if ($DryRun) { $mergeArgs += '--dry-run' }
$out = & node @mergeArgs 2>&1
if ($LASTEXITCODE -ne 0) { Say "settings.json 은 바꾸지 않았습니다: $out" } else { foreach ($l in $out) { Say "settings: $l" } }

if (-not $DryRun) {
  $logFile = Join-Path $dst "devos-install-$stamp.log"
  $log | Set-Content -LiteralPath $logFile -Encoding UTF8
  Write-Host "설치 기록: $logFile"
  Write-Host '완료. Claude Code 를 다시 시작하면 적용됩니다. 확인: claude 실행 후 /agents, /hooks 메뉴와 스킬 목록.'
}
