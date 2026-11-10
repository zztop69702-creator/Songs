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

실기기 시험: **0건** (TEST_PLAN P0-01 ~ P0-10, P1-04·06·07·08, V2-D01 ~ D05 미실시)
