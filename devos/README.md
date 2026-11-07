# 송유성 개발 운영체계 V1.0

Claude Code 가 기존 자산을 지키면서 빠르고 정확하게 앱을 개발·개선하도록 하는 규칙·스킬·서브에이전트·훅 묶음입니다.

## 구성
| 구분 | 위치(이 저장소) | 내용 |
|---|---|---|
| 기본 규칙 | `CLAUDE.md` | A 보호 · B 실행 · C 디자인 · D 검증, 승인 기준 |
| 스킬 10개 | `.claude/skills/` | app-builder · design-system · quality-check · ventilation-expert · tab-report · estimate-builder · contract-docs · agent-blogger · mcp-builder · experience-ai-release(경험곱하기AI 전용) |
| 서브에이전트 4개 | `.claude/agents/` | developer · designer · tech-reviewer · qa-tester |
| 훅 | `.claude/settings.json`, `.claude/hooks/` | 수정 전 백업 · 수정 직후 그 파일 문법/한글 깨짐 검사 · 중요 파일 변경 알림 · 작업 종료 검사 |
| 훅 설정 | `.claude/devos.json` | 검사 확장자, 중요 파일 목록, 작업 종료 시 빠른 테스트 |

## 이 저장소에서
`git pull` 만 하면 됩니다. 이 폴더에서 Claude Code 를 열면 규칙·스킬·에이전트·훅이 자동으로 적용됩니다(처음 열 때 프로젝트 훅 사용 확인이 나올 수 있습니다).

## Windows 의 모든 프로젝트에 적용 (사용자 폴더 설치)
Node.js 가 필요합니다(훅 실행용). 저장소 폴더에서:

```powershell
powershell -ExecutionPolicy Bypass -File devos\install-windows.ps1 -DryRun
```

미리보기로 무엇이 설치·건너뛰어지는지 확인한 뒤:

```powershell
powershell -ExecutionPolicy Bypass -File devos\install-windows.ps1
```

- 이미 있는 스킬·에이전트·훅 파일은 덮어쓰지 않습니다.
- `CLAUDE.md` 가 이미 있으면 보존하고 `CLAUDE.devos.md` 로 따로 둡니다.
- `settings.json` 은 백업(`settings.json.bak-날짜`) 후 훅만 추가합니다. 되돌리려면 백업 파일로 바꾸면 됩니다.
- 설치 기록: `%USERPROFILE%\.claude\devos-install-날짜.log`

## 스킬 직접 부르기
설명이 맞으면 Claude 가 자동으로 고릅니다. 직접 부르려면 Claude Code 에서 `/app-builder`, `/quality-check`, `/ventilation-expert` 처럼 입력합니다.

## 되돌리기
- 저장소: `git revert` 또는 해당 커밋 이전으로.
- 사용자 폴더: 설치 기록에 적힌 항목을 지우고 `settings.json.bak-날짜` 로 복원.
