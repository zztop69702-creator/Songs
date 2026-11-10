# DEVELOPMENT_PLAN.md — 개발 계획

- 작성일: 2026-11-10 / 현재 단계: **Phase 1 + 스마트 비교보정 V2 구현 완료(빌드·단위시험), 실기기 시험 대기**
- 일정·예산은 원 지시서 §8의 예비 가정을 따르며 확정 아님.

## 1. 단계 요약

| 단계 | 범위 | 예비 기간 | 착수 조건 | 상태 |
|---|---|---|---|---|
| Phase 0-A | 문서 검토(본 문서 세트) | 완료 | — | ✅ 완료 |
| Phase 0-B | 기기 진단 → 앱 내 "기기 진단" 화면으로 통합(D-011) | — | 시험 폰 확보 | ✅ 구현 / ⏳ 실기기 결과 대기 |
| Phase 1 | 스마트폰 MVP | 2~4주 | "앱 개발시작" 지시 | ✅ 구현·빌드·단위시험 / ⏳ 실기기 인수시험 |
| V2 보정 | 스마트 비교보정 (docs/CALIBRATION_V2_REVIEW.md) | — | 사용자 V2 지시 | ✅ 로직·UI 구현 / ⏳ 실제 소음계·OCR 시험 |
| Phase 2 | 녹화·WAV/CSV/JSON·거리(수동 기본, AR 보조) | 2~4주 | Phase 1 인수 | ⏸ |
| **Phase 2.5 (제안)** | 다지점 실측 스캔 맵(대안 A) | 1~2주 [추정] | Phase 2 인수 | ⏸ |
| Phase 3 | 자동 음원탐지 — 파일 기반 검증 → 장비 결정 → 연동 | 6~12주 이상 | 파일 검증 통과 + 사업 판단 | ⏸ |
| Phase 4 | PDF 보고서·전후 비교 | 2~4주 | Phase 2 인수(Phase 3 무관하게 가능) | ⏸ |
| Phase 5 | 현장 검증 | 4~8주 | Phase 4 | ⏸ |

**우선순위 제안:** Phase 4(보고서)를 Phase 3보다 먼저 진행 → 영업용 결과물을 조기 확보.

## 2. 모듈 구조 (지시서 §4 준수)

```
app/                         Compose UI, 네비게이션 (네이비 #101E31, 블루 #3478F6)
core/model/                  데이터 모델 (순수 Kotlin, JVM 단위시험 가능)
core/dsp/                    FFTAnalyzer, A-weighting, SoundLevel, BPF 계산 (순수 Kotlin)
feature/camera/              CameraModule (CameraX)
feature/audio/               AudioCaptureModule (AudioRecord, 타임스탬프)
feature/level/               SoundLevelModule (교정 적용)
feature/localization/        SourceLocalizationModule (Phase 3, 배열 없으면 비활성)
feature/distance/            DistanceModule (Manual / ARCore / External)
feature/overlay/             OverlayRendererModule
feature/recorder/            RecorderModule (MP4 무음 + WAV + mux)
data/                        MeasurementRepository (Room/파일)
report/                      ReportGenerator (PDF/CSV/JSON)
```
- `core/*`는 Android 의존성 없이 작성 → 이 컨테이너에서도 단위시험 가능.
- 새 의존성(CameraX, ARCore, Room, PDF 라이브러리 등)은 착수 시 목록·버전·라이선스를 제시하고 승인 후 도입.

## 3. 데이터 모델 초안 (필수 필드 — 지시서 §6)

```kotlin
enum class DataOrigin { MEASURED, ESTIMATED, MANUAL, SIMULATED }
enum class MarkerSource { AUTO, MANUAL, EXPERT_EDITED }          // 실선 빨강 / 점선 빨강 / 노랑
enum class DistanceSource { ARCORE_DEPTH, EXTERNAL_LASER, MANUAL }
enum class CalibrationState { UNCALIBRATED, REFERENCE_COMPARED, CALIBRATOR_VERIFIED }

data class Calibration(
    val state: CalibrationState,
    val offsetDb: Double?,            // UNCALIBRATED면 null
    val referenceMeter: String?,      // 기준 소음계 모델/일련번호
    val calibratedAt: Long?,
    val inputPath: String,            // 내장마이크/USB 장치명
    val audioSource: String,          // UNPROCESSED / VOICE_RECOGNITION
    val preprocessingVerified: Boolean
)

data class SourceMarker(
    val id: String,
    val source: MarkerSource,
    val imageX: Float, val imageY: Float,      // 정규화 좌표
    val timestampNs: Long,
    val originalEstimate: AutoEstimate?,       // AUTO/HYBRID일 때 원래 추정값 보존
    val edits: List<MarkerEdit>,               // 수정 이력 (원본 덮어쓰기 금지)
    val limitations: List<String>              // 반사/다중음원/협대역/분해능
)

data class DistanceReading(
    val source: DistanceSource,
    val meters: Double?,                       // 실패 시 null (값 생성 금지)
    val targetPoint: Pair<Float, Float>?,
    val confidence: Double?,
    val failureReason: String?,
    val note: String = "표면거리이며 음원거리와 다를 수 있음"
)
```

## 4. Phase 0-B 승인 시 작업 범위 (예고)
1. `acoustic-vision/probe/` 최소 Android 앱(단일 화면, 측정 기능 없음)
2. 수집 항목: 기기정보, UNPROCESSED 지원, AGC/NS 효과, AudioRecord 타임스탬프, 카메라 타임스탬프 소스, CameraX+AudioRecord 동시 동작, USB 입력 장치 채널 수/샘플레이트, ARCore/Depth 지원
3. 결과 JSON 내보내기 → 사용자가 TEST_LOG.md에 첨부
4. 빌드·설치는 사용자 Windows PC의 Android Studio에서 수행(이 컨테이너는 Android SDK 없음)

## 5. 단계 종료 보고 형식
구현 기능 / 테스트 명령 및 결과 / 미구현·위험 / 변경 파일 / 다음 승인 요청 — 각 단계 종료 시 CHANGELOG.md·TEST_LOG.md 갱신과 함께 보고.
