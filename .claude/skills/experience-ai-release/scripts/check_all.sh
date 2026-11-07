#!/usr/bin/env bash
# 경험곱하기AI 전체 검증을 한 번에 돌린다.
# 사용법: bash check_all.sh <저장소 루트> <결과 폴더>
#   예: bash .claude/skills/experience-ai-release/scripts/check_all.sh /home/user/Songs /tmp/eai-check
# 하는 일
#   1) 기준본: git HEAD 의 src/app.html (수정 전) → 결과카드 비교(regress)의 기준
#   2) 빌드: build.py 로 <결과 폴더>/dist 에 시험용으로 만든다 (배포 폴더 ai-next-step/dist 는 건드리지 않는다)
#   3) 검사: 흐름(smoke) · 결과카드 비교(regress) · 화면 맞춤과 흐르는 점(layout_flow)
#           · 실제 클릭(verify_clicks) · 10단계 재생/정의 완성(finale) · 다운로드 버튼(download_button)
#           · 지도 비우기 확인 창(reset_all) · 3D 역량 도시(city3d) · 해 본 역할(have_roles) · 두 단계 고르기(twostage)
# 각 검사는 'ERRORS' 또는 '오류' 줄에 앱 오류(스크립트 오류 + 앱 안 자동검사 실패)를 찍는다. 모두 비어 있어야 통과.
set -u
ROOT=${1:?저장소 루트}; OUT=${2:?결과 폴더}
APP="$ROOT/ai-next-step"; HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
git -C "$ROOT" show HEAD:ai-next-step/src/app.html > "$OUT/base_app.html"
python3 -I "$APP/build.py" "$OUT/dist" | tail -2
DIST="$OUT/dist"
BAT=$(ls "$DIST"/*_설치.bat | head -1)
echo "--- 설치 파일 점검"
LC_ALL=C grep -c $'[\x80-\xff]' "$BAT" | sed 's/^/  .bat 안의 비ASCII 줄 수(0이어야 함): /'
python3 -I -c "import zipfile,glob,sys; z=zipfile.ZipFile(glob.glob(sys.argv[1]+'/*.zip')[0]); print('  zip:', z.namelist(), '손상 없음' if z.testzip() is None else '손상')" "$DIST"
sed -n 9p "$BAT" | tr -d '\r' | base64 -d | grep -o '빌드 [0-9]\{4\}-[0-9]\{4\}' | head -1 | sed 's/^/  설치 파일 안 앱 빌드 번호: /'
echo "--- 흐름(smoke)";        node "$HERE/smoke.js" "$APP/src/app.html" "$OUT/smoke" 2>&1 | grep -E 'ERRORS|Error|Timeout'
echo "--- 결과카드 비교(regress, 기준=HEAD)"; node "$HERE/regress.js" "$OUT/base_app.html" "$APP/src/app.html" 2>&1 | tail -20
echo "--- 화면 맞춤·흐르는 점(layout_flow)"; node "$HERE/layout_flow.js" "$APP/src/app.html" "$OUT" 2>&1 | cut -c1-200
echo "--- 실제 클릭(verify_clicks, 배포 파일)"; node "$HERE/verify_clicks.js" "$DIST/online.html" "$OUT" 2>&1 | grep -E '②|④|⑤|오류' | cut -c1-200
echo "--- 10단계 재생·정의 완성(finale)"; node "$HERE/finale.js" "$APP/src/app.html" "$OUT" 2>&1 | grep -E '모두 체크|ERRORS'
echo "--- 지도 비우기 확인 창(reset_all)"; node "$HERE/reset_all.js" "$APP/src/app.html" "$OUT" 2>&1
echo "--- 3D 역량 도시(city3d)"; node "$HERE/city3d.js" "$APP/src/app.html" "$OUT" 2>&1
echo "--- 해 본 역할(have_roles)"; node "$HERE/have_roles.js" "$APP/src/app.html" "$OUT" 2>&1
echo "--- 1쪽 두 단계 고르기(twostage)"; node "$HERE/twostage.js" "$APP/src/app.html" "$OUT" 2>&1
echo "--- 다운로드 버튼(download_button)"; node "$HERE/download_button.js" "$DIST/online.html" "$OUT/online_top.png" "$APP/src/app.html" 2>&1
