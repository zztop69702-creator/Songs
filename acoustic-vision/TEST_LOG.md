# TEST_LOG.md — 시험 기록

유형: `UNIT` 단위시험 / `SIM` 합성신호·시뮬레이션 / `CALC` 이론 계산 / `ENV` 환경 점검 / `JVM-UI` Robolectric 화면 시험 / `BUILD` 빌드·정적검사 / `DEVICE` 실기기 / `FIELD` 현장
**SIM·CALC·JVM-UI 결과는 실측 검증이 아니다.**

| ID | 날짜 | 유형 | 환경 | 명령/방법 | 결과 | 판정 |
|---|---|---|---|---|---|---|
| T-0001 | 2026-11-10 | ENV | 클라우드 Linux 컨테이너(x86_64) | `java -version`, `gradle --version` | OpenJDK 21.0.12 / Gradle 8.14.3. 초기 Android SDK 없음 | — |
| T-0002 | 2026-11-10 | CALC | Python 3.13 | 배열 분해능·앨리어싱 계산 | FEASIBILITY §5.2 표 | 이론값 |
| T-0003 | 2026-11-10 | CALC | Python 3.13 | f=N/60, f_BPF=N×Z/60 | 1450 rpm·40날개 → 24.2 / 966.7 Hz 등 | 이론값 |
| T-0004 | 2026-11-10 | ENV | 컨테이너 | Android cmdline-tools 설치: platform 36·37, build-tools 36, platform-tools | 설치 성공. KVM 없음 → 에뮬레이터 불가 | 실기기·에뮬레이터 시험 불가 |
| T-0005 | 2026-11-10 | ENV | 컨테이너 | Gradle 9.8.2 배포본 다운로드 | downloads.gradle.org 차단(허용 요청 거부됨) | AGP 8.13.2 + Gradle 8.14.3로 구성 (D-010) |
| T-0006 | 2026-11-10 | UNIT/SIM | 컨테이너 JVM | `gradle :core:test` | **37 / 37 통과** (DSP 16, 모델 7, 비교보정 V2 14) | 합성 신호 기준 통과 |
| T-0007 | 2026-11-10 | UNIT/SIM | 〃 | A가중 IIR(fs 48 kHz) vs IEC 해석식 편차 | 10 Hz–4 kHz ≤ ±0.29 dB, 5 kHz +0.43, 8 kHz +0.70, 10 kHz +0.59, 12.6 kHz −0.20, 16 kHz **−2.85**, 20 kHz −11.6 dB | 10 kHz 이하 사용 권장. 16 kHz 이상 LA 과소평가 — 한계로 기록 |
| T-0008 | 2026-11-10 | BUILD | 〃 | `gradle :app:assembleDebug` | 성공, app-debug.apk 36.6 MB (ARM 전용, ML Kit OCR 포함) | — |
| T-0009 | 2026-11-10 | BUILD | 〃 | `gradle :app:lintDebug` | 오류 0, 경고 4 (targetSdk 36, 세로고정 2, allowBackup) | 허용 (DECISIONS D-010, D-018) |
| T-0010 | 2026-11-10 | JVM-UI | Robolectric 4.16.1, SDK 35 | `gradle :app:testDebugUnitTest` | **7 / 7 통과**: 홈·면책표시, 현장 등록 검증·저장, 측정 JSON·수동 마커 재표시, 원본 덮어쓰기 거부, 기기진단 JSON 생성, 하드웨어 없는 환경에서 측정화면 비정상종료 없음, V2 보정 설정 단계 필수조건 | 화면 흐름 확인. 카메라·마이크 실동작은 미검증 |
| T-0011 | 2026-11-10 | ENV | 〃 | Robolectric android-all jar | Maven Central 429(요청 제한) → Google Maven Central 미러에서 수신, SHA-1 일치 확인 | — |
| T-0012 | 2026-11-10 | UNIT/SIM | 컨테이너 JVM | `gradle :core:test` (연속판독 추가) | **43 / 43 통과** (연속판독 6건 추가) | 합성 데이터 기준 |
| T-0013 | 2026-11-10 | JVM-UI | Robolectric | `gradle :app:testDebugUnitTest` | 1차 1건 실패: 보정 단계 전환 후 제목이 화면 밖 (스크롤 위치 유지 결함) → 수정 후 **7 / 7 통과** | 결함 수정 (D-022) |
| T-0014 | 2026-11-10 | BUILD | 〃 | `assembleDebug`, `lintDebug` | APK 36.7 MB, Lint 오류 0 / 경고 5 (ChromeOsAbiSupport 추가: ARM 전용 설정에 따른 것) | — |
| T-0015 | 2026-11-10 | BUILD | 컨테이너 | 일회용 시험 키로 release 서명 빌드 (CI_BUILD_NUMBER=7) | 성공: versionCode 1007, 서명된 app-release.apk(32.2 MB). 키는 시험 후 삭제 | 서명 설정 동작 확인 |
| T-0016 | 2026-11-10 | BUILD | 컨테이너 | 워크플로 "이름 지정·SHA-256" 단계 추출 실행 | 1차: 파일명 빌드번호 중복 결함 발견 → 수정 후 `…-0.4.0-b7-release.apk` 등 생성, `sha256sum -c` OK, apksigner 서명지문 출력 | 결함 수정 |
| T-0017 | 2026-11-10 | BUILD | 컨테이너 | 병합 매니페스트 권한 검사 | ML Kit이 INTERNET·ACCESS_NETWORK_STATE 자동 병합 발견 → INTERNET 제거 확인 | D-028 |
| T-0018 | 2026-11-10 | UNIT | 컨테이너 JVM | BackupTest | **5 / 5 통과**: 내보내기→복원, 덮어쓰기 금지, zip-slip 차단, 변조 탐지(SHA-256), manifest 없음 거부 | — |
| T-0019 | 2026-11-10 | JVM-UI | Robolectric | 앱 정보 화면, Fold5 펼친 화면 크기 가정(w841dp·h673dp, 추정) 흐름 | 1차 실패 2건: 시험이 비동기 파일 IO를 기다리지 않음(앱 결함 아님) → 대기 추가 후 **14 / 14 통과, 3회 연속** | 시험 안정화 |
| T-0020 | 2026-11-10 | CI | GitHub Actions #1 (ubuntu-latest, JDK 17.0.21) | 푸시 자동 실행 | **실패**: 큰 화면 시험 1건 — 느린 CI에서 비동기 화면 로딩 전 클릭(시험 타이밍 결함) | 대기 추가로 수정 |
| T-0021 | 2026-11-10 | CI | GitHub Actions #2 | 시험 → debug APK → SHA-256 → 아티팩트 | **성공** (약 4분): core 43 + app 14 통과, `YOUNGIL-AcousticVision-0.4.0-b2-debug.apk`(36.6 MB), 아티팩트 ZIP SHA-256 `00bbb27f…0a7e36`, release는 secrets 없어 생략(설계대로) | 클라우드 빌드 경로 확인. 실기기 설치는 미검증 |
| T-0022 | 2026-11-10 | DEVICE(사용자 보고) | Galaxy Z Fold5 | 설치 | 사용자 보고 "앱 설치됐어" → **2026-11-11 정정**: 런처 검색 캡처에 "영일 음향비전"·"(시험)" 모두 없음, "영업도우미"(명함 앱)만 존재 → 본 앱은 미설치로 판정 | T02 미완료 |
| T-0023 | 2026-11-10 | BUILD | 컨테이너 | `tools/build-release.sh` RELEASE_SEQ=1 (정식 키) | 성공, 32.2 MB, 같은 소스 재빌드 시 SHA-256 동일(재현 가능 빌드) | 드라이브 업로드 한도(10 MB) 초과로 미배포 |
| T-0024 | 2026-11-10 | BUILD | 〃 | OCR Play 서비스형 + R8 축소 | 2.76 MB, 인터넷 권한 없음, 축소 APK에 저장 모델·직렬화 클래스 존재 확인(dexdump) | 실기기 동작은 미검증 |
| T-0025 | 2026-11-10 | UNIT/JVM-UI | 〃 | 전환 후 전체 시험 | core 43 + app 14 통과, Lint 오류 0 | — |
| T-0026 | 2026-11-11 | BUILD | 컨테이너 | `tools/build-release.sh` RELEASE_SEQ=3 | 0.4.0-r3 (versionCode 2003, 2.76 MB, 서명 9f21c817…0cf1), 시험 57건 통과 → 드라이브 01_설치파일 업로드(크기 일치). r2는 휴지통 | 실기기 미검증 |
| T-0027 | 2026-11-11 | DEVICE(사용자 진행) | Galaxy Z Fold5 | 드라이브 설치 안내 1~3단계 | 사용자: 3단계(다운로드)까지 확인, 4단계(설치) 화면 미확인. r3 APK 정적 검사 정상(라벨·런처 활동·아이콘). 대화창으로 동일 APK 직접 전달 | 진행 중 |

실기기 시험: **0건** (TEST_PLAN P0-01 ~ P0-10, P1-04·06·07·08, V2-D01 ~ D05 미실시)
