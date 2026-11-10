# DEVELOPMENT_PLAN.md — 개발 계획

문서 버전: 0.1 (2026-11-10) · 현재 단계: **Phase 0 완료 보고 / Phase 1 승인 대기**

## 1. 단계 및 게이트
| Phase | 범위 | 기간(SPEC 예비) | 진입 게이트 | 상태 |
|---|---|---|---|---|
| 0 | 기술검증 문서 | — | — | **완료(문서), 승인 대기** |
| 1 | 스마트폰 MVP | 2~4주 | Phase 0 승인, 시험 기기 확보 | 대기 |
| 2 | 영상·거리 | 2~4주 | P1 인수시험 통과 | 대기 |
| 3A | **AR 스캔 히트맵** (자동 위치 표시 기본, D-012) | 4~6주 (추정) | P2 통과 (ARCore 거리 모드·오디오 동기 재사용) | 대기 |
| 3B | 대형 배열 빔포밍 (선택 확장) | 6~12주+ (SPEC) | **Go/No-Go**: 3A 결과 부족 판단 + T-10(시뮬레이션) 통과 + 임대 대비 비용 검토 | 보류 |
| 4 | 보고서 | 2~4주 | P2 통과 (P3와 병행 가능) | 대기 |
| 5 | 현장검증 | 4~8주 | P4 통과 | 대기 |

**Phase 3A 범위 (D-012):** ARCore 세션 + 외장 마이크 동시 취득 → 스캔 샘플(위치·자세·대역 레벨·추적 상태) 기록 → 대상 표면(평면 적합 또는 Depth) 투영 → 보간 반경 제한 히트맵 → 대역 선택(BPF/f_rot/120 Hz/사용자 대역) → 영상 오버레이·저장. 내장 마이크 기준 채널로 운전 변동 정규화.

**권장 순서 조정(제안):** Phase 4(보고서)를 Phase 3보다 먼저 수행. 영업 가치가 큰 "FFT+영상+전후비교 보고서"를 먼저 확보하고, 불확실성이 큰 AUTO는 별도 게이트로 분리한다. → DECISIONS D-008 (제안).

## 2. Phase 1 상세 범위 (승인 시 착수)
**포함**
1. Android 프로젝트 골격 (Kotlin, Compose, 단일 모듈 + 패키지 단위 모듈 분리; Gradle 멀티모듈은 Phase 2 이후 판단)
2. 기기 진단 화면 (T-02 항목 자동 점검·내보내기)
3. CameraX 미리보기
4. AudioRecord 입력 (소스 선택 로직, 효과 상태 기록)
5. FFT(로그 주파수축) + 스펙트로그램 + 피크 표시, fs/N/윈도/Δf 표기
6. 참고 소음도: A가중 필터, LAeq, LAFmax, LAFmin — **미교정 상태 라벨 고정**
7. rpm·날개수 입력 → f_rot, BPF 및 고조파 마커를 스펙트럼에 표시
8. 수동 음원 마커(빨간 점선, "수동" 텍스트) 저장·재표시
9. 프로젝트/측정 기본 저장(Room DB) — 아래 데이터 모델의 필수 필드 포함
10. 권한 거부·중단 처리

**제외(후속 단계)**: MP4 녹화, 오버레이 굽기, ARCore, 교정 기능 UI(필드만 존재), PDF, 다채널

**신규 의존성(승인 요청 대상)**
| 라이브러리 | 용도 | 대안 |
|---|---|---|
| androidx.camera (core, camera2, lifecycle, view) | 카메라 | — (SPEC 지정) |
| Jetpack Compose BOM, Navigation | UI | — |
| Room | 로컬 DB | 파일(JSON) 저장 |
| FFT: 자체 구현(radix-2) 또는 JTransforms | FFT | 자체 구현 시 의존성 0, 단위시험 필수 |
| kotlinx.serialization | JSON 메타데이터 | Gson |
| JUnit, AndroidX Test | 시험 | — |

## 3. 소프트웨어 구조 (SPEC §4 반영)
```
app/
 ├─ camera/        CameraModule (CameraX)
 ├─ audio/         AudioCaptureModule (AudioRecord, 타임스탬프, 소스/효과 기록)
 ├─ level/         SoundLevelModule (A/C/Z 가중, Leq, Fast/Slow, 교정 오프셋)
 ├─ fft/           FFTAnalyzerModule (FFT, 스펙트로그램, 피크, 1/3옥타브-참고)
 ├─ scanmap/       AR 스캔 히트맵 (Phase 3A, D-012)
 ├─ localization/  SourceLocalizationModule (Phase 3B, 배열 미연결 시 비활성)
 ├─ distance/      DistanceModule (Phase 2: ARCore 별도 모드 / 외장 / 수동)
 ├─ overlay/       OverlayRendererModule
 ├─ recorder/      RecorderModule (Phase 2)
 ├─ data/          MeasurementRepository (Room)
 ├─ report/        ReportGenerator (Phase 4)
 └─ diagnostics/   기기 능력 진단 (Phase 0/1)
```

## 4. 데이터 모델 초안 (설계안 — 코드 아님)
필수 필드는 **굵게**. 모든 수치 레코드는 `provenance`를 가진다.

```text
enum Provenance        { MEASURED, ESTIMATED, MANUAL, SIMULATED }
enum MarkerSource      { AUTO, MANUAL, EXPERT_EDITED }
enum DistanceSource    { AR_DEPTH, AR_RAW_DEPTH, EXTERNAL_LASER, MANUAL }
enum CalibrationStatus { UNCALIBRATED, REFERENCE_COMPARED, EXTERNAL_MIC_CALIBRATED }

Project      { id, siteName(학교/현장명), createdAt, notes }
Equipment    { id, projectId, name(설비명), type(송풍기/모터/덕트/소음캐비닛), rpm?, bladeCount?, nameplate? }
Measurement  { id, projectId, equipmentId, phase(BEFORE/AFTER/NONE), operatingCondition, location,
               startedAt, durationSec, deviceModel, osVersion, audioSource, effectsState(AGC/NS/AEC),
               inputDevice(내장/USB 모델), sampleRate, fftSize, window,
               **calibration: CalibrationRecord**, files[] }
CalibrationRecord { **status**, offsetDb?, referenceDevice?, referenceSerial?, calibratedAt?, method }
LevelResult  { measurementId, **provenance**, weighting(A/C/Z), timeWeighting(F/S), leq, lmax, lmin, unitLabel }
SpectrumPeak { measurementId, **provenance**, freqHz, levelDbfs, levelDbSpl?(교정 시만), candidate(BPF/f_rot/120Hz/…) }
SourceMarker { id, measurementId, **source: MarkerSource**, x, y(정규화 영상좌표), timestampMs,
               freqBand?, originalEstimate?(AUTO 원값), editHistory[], editor?, confidence?, note }
DistanceRecord { id, measurementId, **source: DistanceSource**, valueM?(실패 시 null), targetX, targetY,
               **confidence**, failed: Boolean, failReason? }
FileRef      { type(MP4/WAV/CSV/JSON), path, sha256, createdAt, isOriginal }
AnnotationEdit { id, targetId, before, after, editedAt, editor }   // 원본 파일과 분리 저장

// Phase 3A — AR 스캔 히트맵 (D-012, D-013)
ScanSession  { id, measurementId, startedAt, endedAt, bandDefs[], referenceChannel(내장마이크 사용 여부),
               surfaceModel(PLANE/DEPTH), **calibration: CalibrationRecord** }
ScanSample   { scanId, timestampNs, poseX/Y/Z, poseQuat, **trackingState**, micToSurfaceM?,
               bandLevels[], refLevel?, **provenance = MEASURED**, discarded: Boolean, discardReason? }
HeatmapCell  { scanId, band, u, v(표면 좌표), value?(미측정이면 null), **provenance(MEASURED/ESTIMATED)**,
               sampleCount, interpRadiusM }
```

## 5. UI 원칙
- 네이비 #101E31(배경), 블루 #3478F6(주요 동작), 빨간색 음원 마커.
- 마커: AUTO=빨간 실선, MANUAL=빨간 점선, EXPERT_EDITED=노란색 + 항상 텍스트 라벨 병기(색약 대응).
- 단위 라벨: dBFS(상대)와 dB(A)(참고·교정 상태) 위젯 분리.
- 측정 화면 상단에 상태 띠: "미교정 / 기준기 비교교정(날짜) / 외장마이크 교정".

## 6. 단계 종료 보고 양식 (SPEC §6)
구현 기능 · 테스트 명령 및 결과 · 미구현/위험 · 변경 파일 · 다음 승인 요청.

## 7. 사용자 준비 사항 (Phase 1 착수 전)
1. 시험용 Android 기종·OS 버전 알림 (가능하면 2종).
2. Windows PC에 Android Studio 설치 및 USB 디버깅 연결 확인.
3. 기준 소음계 보유/대여 가능 여부.
4. DECISIONS.md "제안" 항목 승인/수정.
