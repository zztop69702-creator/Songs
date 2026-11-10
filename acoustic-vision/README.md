# YOUNGIL Acoustic Vision

학교 급식실 송풍기·모터·덕트·소음캐비닛의 소음도, 주파수, 음원 위치, 거리, 영상 기록을 통합하는 Android 현장 진단 앱.

> **현재 상태 (2026-11-10): Phase 1 스마트폰 MVP + 스마트 비교보정 V2 구현, 빌드·단위시험 통과. 실기기 시험 0건.**
> 본 앱의 소음도 값은 참고값이며 공인 소음계 측정을 대체하지 않습니다. 자동 음원탐지(AUTO)는 구현되지 않았습니다(동기화 마이크 배열 필요, Phase 3).

## 구현된 기능 (v0.2.0)
| 기능 | 내용 | 검증 상태 |
|---|---|---|
| 현장 등록 | 학교/현장명, 설비명, 운전조건, RPM, 날개 수, 측정위치, 개선 전/후, 촬영 승인 확인 | JVM 화면 시험 통과 |
| 측정 화면 | 카메라 미리보기 + 실시간 LAF/LAeq/LAFmax/LAFmin(A가중), FFT 스펙트럼(로그축·dBFS), 피크, 회전/BPF 예상선 | 빌드만 확인 — **실기기 미시험** |
| 수동 마커 | 화면 탭 → 빨간 점선 마커(출처 "수동" 병기), 저장·재표시 | JVM 화면 시험 통과 |
| 저장 | 측정 JSON(원본 dBFS + 보정 오프셋 분리) + 화면 스냅샷, 원본 덮어쓰기 금지, 수동 거리(출처 기록) | 단위시험 통과 |
| 스마트 비교보정 V2 | 기준 소음계 LAeq 비교, 무음(진동) 시작신호, 변동성·시간민감도 판정, 30 s 재측정, 결과화면 촬영+OCR(보조), 사용자 확인, 안전장치, 독립 검증구간, 승인 후 활성화, 프로파일 무효화 | 로직 단위시험 통과 — **실제 소음계·OCR 미시험** |
| 음향교정기 보정 | 외장 측정 마이크 + 94/114 dB 교정기 | 빌드만 확인 |
| 기기 진단 | UNPROCESSED 지원, AGC/NS 해제, 입력장치 채널수·샘플레이트, 오디오 타임스탬프, 카메라 타임스탬프 기준 → JSON | JVM 화면 시험 통과 (실기기 값 미수집) |

## 설치 (시험용 debug APK)
1. `app/build/outputs/apk/debug/app-debug.apk`를 휴대폰에 복사 → 설치 (출처를 알 수 없는 앱 허용 필요)
2. 처음 실행 시 마이크·카메라 권한 허용
3. **기기 진단 → 진단 실행 → 결과 공유**로 JSON을 보내주시면 TEST_LOG에 기록합니다.

## 빌드 (Windows · Android Studio)
- Android Studio 최신 안정판, JDK 17 이상 (Android Studio 내장 JBR 사용 가능)
- 이 폴더(`acoustic-vision`)를 Android Studio로 열기 → Gradle Sync → Run
- 명령줄: `gradlew.bat :app:assembleDebug` / 시험: `gradlew.bat :core:test :app:testDebugUnitTest`
- 구성: AGP 8.13.2, Gradle 8.14.3(wrapper), Kotlin 2.4.21, compileSdk/targetSdk 36, minSdk 26 (DECISIONS D-010)

## 문서
| 문서 | 내용 |
|---|---|
| [FEASIBILITY.md](FEASIBILITY.md) | 기술 타당성, 물리적 한계, 대안, 법적 검토 |
| [HARDWARE_MATRIX.md](HARDWARE_MATRIX.md) | 장비 후보·구매 전 확인 |
| [RISKS.md](RISKS.md) | 위험 목록 |
| [TEST_PLAN.md](TEST_PLAN.md) | 시험 계획 |
| [docs/CALIBRATION_V2_REVIEW.md](docs/CALIBRATION_V2_REVIEW.md) | 스마트 비교보정 V2 검토·영향도·시험계획 |
| [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) | 단계 계획·구조 |
| [DECISIONS.md](DECISIONS.md) | 의사결정 기록 |
| [TEST_LOG.md](TEST_LOG.md) | 시험 기록 |
| [CHANGELOG.md](CHANGELOG.md) | 변경 이력 |
| [docs/spec/](docs/spec/) | 기준 지시서 V1.0, 스마트 보정 V2.0 |

## 표기 원칙
실측 / 추정 / 수동 입력 / 시뮬레이션을 화면·저장 데이터·문서에서 항상 구분합니다.
마커 출처(auto/manual/expert-edited), 거리 출처(AR/외장/수동), 보정 상태는 필수 필드입니다.
앱이 산출하는 오프셋은 "보정"이며 공인 "교정"이 아닙니다.

## 서비스 제공 주체
진단 서비스 및 계약 주체: 영일 (보고서에 측정 수행자·장비·보정 상태 명시)
