# V3 PHASE 0 검증표 — Galaxy Z Fold5 직접 설치형 APK

- 작성일: 2026-11-10
- 기준: `docs/spec/YOUNGIL_Acoustic_Vision_V3_Android_APK_Installation_Spec.md` (첨부 docx와 내용 동일 확인 — 차이는 서식 기호뿐)
- 진행 근거: 사용자 지시 "추가사항과 종전의 지시사항을 함께 검토 후 진행"
- 표기: [확인] 실행·문서로 확인 / [추정] / [미확인] / [실기기 필요]
- **실제 Galaxy Z Fold5 설치·실행 검증: 0건. "설치 검증 완료"가 아님.**

## 1. 결론
1. **PC 없이 개발·빌드·배포하는 경로는 성립한다.** Claude 모바일(Claude Code 클라우드 세션)에서 저장소 수정·커밋·푸시가 되고, GitHub Actions가 클라우드에서 시험·APK 생성·SHA-256 기록을 한다.
2. **업데이트 시 데이터를 보존하려면 고정 release 서명키가 반드시 필요하다.** 서명키 없이 만든 debug APK는 빌드 환경마다 서명이 달라, 다음 버전을 위에 설치할 수 없다(삭제 후 재설치 → 데이터 소실). → **서명키 생성·보관 방식 결정이 다음 진행의 전제**(§5).
3. 저장소가 **공개(public)** 다. 소스와 아티팩트가 외부에 노출된다(아티팩트는 GitHub 로그인 사용자 다운로드 가능 [추정]). 비공개 전환 또는 별도 비공개 저장소를 권장한다(§6).
4. 한국은 2026년 9월 Android 개발자 인증 1차 적용국이 아니며 사이드로딩은 현재 가능하다 [확인: 보도자료 기준]. **2027년 전 세계 확대 예정** — 그 전에 개발자 등록(소규모 배포 계정 등) 검토 필요.

## 2. 항목별 검증표

| # | 항목 | 결과 | 근거 | 구분 |
|---|---|---|---|---|
| 1 | Claude 모바일에서 저장소 접근·수정 | 가능: 이 세션이 `zztop69702-creator/Songs`를 읽고 커밋·푸시함 | 커밋 64680a8, 2699628, aeaf21a | [확인] |
| 2 | 저장소 공개 범위 | **public** | 저장소 목록 조회 결과 | [확인] |
| 3 | 저장소 내 비밀값 노출 | 발견 안 됨 (bizcard Solapi 키는 `.gitignore`된 로컬 파일) | 코드·이력 검색 | [확인] |
| 4 | 기본 브랜치 | `claude/focused-lovelace-rsg2c1` → 수동 실행 버튼은 워크플로가 이 브랜치에 있어야 표시 | git 원격 조회 | [확인] |
| 5 | GitHub Actions 워크플로 | `.github/workflows/android-apk.yml` 작성: 수동 실행 + `claude/**` 푸시 자동 실행, 시험→debug APK→(키 있으면) release APK→이름·SHA-256·서명지문 요약→아티팩트 30일 | 파일 | [확인: 작성] |
| 6 | 워크플로 핵심 단계 모의 실행 | 일회용 키로 release 서명 빌드 성공(versionCode 1007), 이름 지정·SHA-256·apksigner 서명지문 단계 실행 성공, `sha256sum -c` OK. 모의 실행에서 파일명 중복 결함 발견·수정 | 이 환경 실행 | [확인] |
| 7 | 실제 GitHub Actions 실행 | 푸시 후 실행 결과로 확인 (TEST_LOG 참조) | — | 아래 TEST_LOG |
| 8 | Actions 버전 | checkout@v7, setup-java@v5, setup-gradle@v5, upload-artifact@v6 (각 태그 존재 확인, 최신 대신 안정 쪽 선택) | git 태그 조회 | [확인] |
| 9 | 서명키 보관 | 키는 저장소에 넣지 않음. Base64 + 비밀번호 3종을 **Actions secrets**로만 전달, 로그 미출력 | 워크플로·build.gradle.kts | [확인: 설계] |
| 10 | 업데이트 체계 | applicationId 고정(`kr.youngil.acousticvision`), versionCode = 1000 + 빌드번호(항상 증가), 같은 release 키 | build.gradle.kts | [확인: 설계] |
| 11 | debug APK | 별도 앱 ID(`…​.debug`), 이름 "영일 음향비전 (시험)" — 현장용 release와 공존, 업데이트 불가 명시 | build.gradle.kts | [확인] |
| 12 | 오프라인 동작 | 계측·저장·OCR(번들 모델)·보정 모두 네트워크 불필요. **ML Kit이 INTERNET·ACCESS_NETWORK_STATE 권한을 자동 병합하는 것을 발견 → INTERNET 제거**(앱이 데이터를 외부로 보낼 수 없음). ACCESS_NETWORK_STATE는 상태 조회만 가능하고 제거 시 라이브러리 오류 위험이 있어 유지 | 병합 매니페스트 검사 | [확인: 빌드] / 제거 후 OCR 동작은 [실기기 필요] |
| 13 | 데이터 보존(업데이트) | 앱 전용 저장소(filesDir) JSON·JPEG. 같은 서명 업데이트 시 유지, 삭제 시 소실 | Android 동작 | [확인: 문서 지식] / T11 [실기기 필요] |
| 14 | 데이터 내보내기·복원 | ZIP + manifest(SHA-256), 복원 시 경로검증·무결성검사·덮어쓰기 금지 | BackupTest 5건 통과 | [확인: JVM] |
| 15 | 앱 정보 화면 | 버전·versionCode·빌드번호·서명 인증서 SHA-256(빌드 요약과 같은 형식)·남은 공간 | 화면 시험 통과 | [확인: JVM] |
| 16 | Fold5 펼친 화면 | Android 16 + targetSdk 36 앱은 큰 화면(sw ≥ 600dp)에서 세로 고정이 무시됨 → API 36 한정 opt-out 속성 추가, 접기·펴기 시 상태 유지(configChanges). 큰 화면 가정 화면 시험 통과 | Android Developers Blog, 화면 시험 | [확인: 문서·JVM] / [실기기 필요] |
| 17 | Fold5 Android 버전·보안패치 | 미확인 — 기기에서 설정 › 휴대전화 정보 › 소프트웨어 정보 확인 필요 | — | [실기기 필요] |
| 18 | Fold5 ARCore Depth | 공식 지원 목록에서 확인하지 못함 | 검색 | [미확인] |
| 19 | 삼성 자동 차단(Auto Blocker) | 켜져 있으면 승인된 스토어 외 설치 차단. 삼성 안내: 신뢰하는 앱이면 **일시적으로** 끄고 설치. 끈 상태에서 받은 앱은 다시 켜면 그 경로로 업데이트가 막힐 수 있음 | 삼성 고객지원 | [확인: 공식 안내] |
| 20 | 출처를 알 수 없는 앱 설치 | 다운로드한 앱(브라우저 또는 내 파일)에 "이 출처 허용"을 사용자가 직접 승인 | Android 표준 | [확인: 일반] |
| 21 | Android 개발자 인증 | 2026-09-30 브라질·인도네시아·싱가포르·태국부터 적용, 2027년 전 세계 확대 예정. 미인증 앱은 고급 사이드로딩 절차/ADB로 설치 | 보도(1차 출처 아님) | [확인: 2차 자료] — 한국 적용일 재확인 필요 |
| 22 | 휴대폰에서 SHA-256 확인 | 안드로이드 기본 기능에 파일 해시 계산 없음 → 별도 앱 필요. 대안: 앱 정보 화면의 **서명 인증서 SHA-256**을 빌드 요약과 대조(같은 키 확인) | — | [확인] |
| 23 | 아티팩트 다운로드 | GitHub 로그인 상태에서 Actions 실행 화면 → Artifacts → ZIP 다운로드 → 내 파일에서 압축 해제 → APK 실행 | GitHub 동작 | [추정] — T01에서 확인 |

## 3. 휴대폰 전용 설치 절차 (초안 — T01~T04로 검증 예정)
1. 설정 › 휴대전화 정보: 모델명(SM-F946N)·Android 버전·저장공간 확인
2. 휴대폰 브라우저로 GitHub 로그인 → 저장소 › Actions › "Android APK (Acoustic Vision)" › 최신 성공 실행 › 요약의 **SHA-256·서명지문** 확인 → Artifacts 다운로드
3. 내 파일 › 다운로드 › ZIP 압축 해제 → `…-release.apk` (서명키 설정 전에는 `…-debug.apk`)
4. APK 열기 → Android 경고 확인 → 해당 앱(내 파일/브라우저)의 **출처 허용을 직접 승인**. 자동 차단이 막으면 삼성 공식 안내에 따라 판단(무조건 끄지 말 것, 설치 후 다시 켜기 권장)
5. 실행 → 카메라·마이크 권한 허용 → 앱 서랍 아이콘 확인 → 길게 눌러 홈 화면에 추가
6. 앱 › 정보·백업에서 버전·서명지문이 빌드 요약과 같은지 확인
7. 업데이트 전: 정보·백업 › 전체 데이터 내보내기 → 같은 서명·더 큰 versionCode APK를 위에 설치

## 4. V3 단계 ↔ 현재 구현
| V3 단계 | 내용 | 현재 |
|---|---|---|
| PHASE 0 | 조사·검증표 | 본 문서 |
| PHASE 1 | 설치 가능한 최소 APK(아이콘·권한·오프라인 저장·기본 UI) | 기능은 그 이상 구현됨. **실기기 설치 검증만 남음** (T01~T04, T07, T10) |
| PHASE 2 | 카메라·오디오·FFT·수동 마커·녹화, 15분 시험 | 녹화 외 구현. 녹화(MP4·WAV)는 미구현 |
| PHASE 3 | AR 거리·스마트 소음보정 V2·보고서 | V2 보정(연속판독 포함) 구현, AR·보고서 미구현 |
| PHASE 4 | 외장 마이크 배열 | 별도 승인 대상 |

## 5. 결정 필요 ① — release 서명키 생성 방식
| 방식 | 장점 | 위험 |
|---|---|---|
| A. 이 세션에서 생성해 키 파일과 secrets 등록용 값을 전달 → 사용자가 휴대폰 브라우저로 GitHub secrets 4개 등록 | PC 불필요, 바로 진행 | 개인키가 클라우드 세션·채팅 파일을 거침. 사용자가 안전한 곳(비밀번호 관리자 등)에 보관해야 함 |
| B. 사용자가 PC나 신뢰하는 환경에서 직접 생성 | 키 노출 경로 최소 | PC 필요 (V3 목표와 충돌) |
| C. 키 없이 debug로만 시험 계속 | 준비 불필요 | 매 빌드 삭제 후 재설치 → 데이터 소실, T11 불가 |
**권고: A** (편의) 또는 B (보안). **키를 잃으면 같은 앱으로 업데이트할 수 없음** — 2곳 이상 보관 필수.

## 6. 결정 필요 ② — 저장소 공개 범위
- 현재 public: 소스·APK 아티팩트·빌드 요약이 외부에 노출. 영업용 기술자산 보호 관점에서 **비공개 전환 권고**.
- 비공개 시 GitHub Actions 무료 사용시간 한도 적용(요금제별 상이 — 확인 필요). 이 빌드 1회 예상 10분 내외 [추정].
- 전환은 GitHub 웹 › Settings › General › Danger Zone에서 사용자가 직접 수행.

## 출처
- Android 16 방향·크기 제한 변경: https://android-developers.googleblog.com/2025/01/orientation-and-resizability-changes-in-android-16.html
- 삼성 자동 차단: https://www.samsung.com/uk/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/
- 개발자 인증 일정: https://www.helpnetsecurity.com/2026/06/19/android-developer-verification-rollout-markets/ , https://www.androidauthority.com/android-sideloading-changes-timeline-3679204/ , https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html
- ARCore 지원 기기: https://developers.google.com/ar/devices
