#!/usr/bin/env bash
# 정식(release) APK 빌드 — 구글 드라이브 배포용 (GitHub 불필요)
# 사용: RELEASE_SEQ=<배포순번> ANDROID_KEYSTORE_FILE=<키 경로> ANDROID_KEYSTORE_PASSWORD=... \
#       ANDROID_KEY_ALIAS=youngil-acousticvision ANDROID_KEY_PASSWORD=... tools/build-release.sh
# 결과: dist/YOUNGIL-AcousticVision-<버전>-release.apk, dist/SHA256SUMS.txt, dist/BUILD_INFO.txt
# 비밀번호·키는 출력하지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${RELEASE_SEQ:?RELEASE_SEQ(배포 순번) 필요}"
for v in ANDROID_KEYSTORE_FILE ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  [ -n "${!v:-}" ] || { echo "$v 필요" >&2; exit 1; }
done
[ -f "$ANDROID_KEYSTORE_FILE" ] || { echo "서명키 파일 없음" >&2; exit 1; }
GRADLE=${GRADLE:-./gradlew}
"$GRADLE" --no-daemon -q :core:test :app:testDebugUnitTest :app:assembleRelease
meta=app/build/outputs/apk/release/output-metadata.json
apk="app/build/outputs/apk/release/$(jq -r '.elements[0].outputFile' "$meta")"
case "$apk" in *unsigned*) echo "서명되지 않음 — 중단" >&2; exit 1;; esac
vname=$(jq -r '.elements[0].versionName' "$meta"); vcode=$(jq -r '.elements[0].versionCode' "$meta")
rm -rf dist && mkdir -p dist
out="dist/YOUNGIL-AcousticVision-${vname}-release.apk"
cp "$apk" "$out"
APKSIGNER=$(ls -d "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/build-tools/*/ | sort -V | tail -1)apksigner
certinfo=$("$APKSIGNER" verify --print-certs "$out")
cert=$(printf '%s\n' "$certinfo" | awk '/certificate SHA-256 digest/ {print $NF}' | head -n1)
(cd dist && sha256sum "$(basename "$out")" > SHA256SUMS.txt)
{
  echo "앱: 영일 음향비전 (YOUNGIL Acoustic Vision)"
  echo "파일: $(basename "$out")"
  echo "버전: $vname (versionCode $vcode)"
  echo "APK SHA-256: $(cut -d' ' -f1 dist/SHA256SUMS.txt)"
  echo "서명 인증서 SHA-256: $cert"
  echo "빌드 시각(UTC): $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "소스 커밋: $(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
  echo "시험: core + app 단위/화면 시험 통과 (JVM, 실기기 아님)"
} > dist/BUILD_INFO.txt
cat dist/BUILD_INFO.txt
