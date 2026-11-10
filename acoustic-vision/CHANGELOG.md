# CHANGELOG.md

## [0.2.0 — Phase 1 + 스마트 비교보정 V2] 2026-11-10
### 추가
- Android 프로젝트 (`core` 순수 Kotlin 모듈 + `app` Compose 앱)
- core/dsp: radix-2 FFT, Hann 창, dBFS(AES17), A가중(해석식·IIR), LAF/LAeq/LAFmax/LAFmin/LZeq, 피크 검출, 1/3 옥타브(FFT 근사), 회전·BPF 계산과 피크 매칭, 스펙트럼 평균
- core/model: Project, Measurement, SourceMarker(출처 필수), DistanceReading(출처 필수, 실패 시 값 없음), Calibration, PreprocessingStatus, AudioConfig
- core/calibration (V2): WindowAnalyzer(100 ms 블록, 1초값 변동, ±1 s 시간민감도), 15 s→30 s 판정, SafetyGate(자동 확정 금지 11개 사유), OffsetCalculator·검증, CalibrationProfile(V2 §6 필드), ProfileValidity(무효화), MeterOcrParser
- app: 현장 목록/등록, 측정 화면(CameraX 미리보기 + 실시간 소음도·FFT + 수동 마커), 측정 저장·재표시, 보정 허브, 스마트 비교보정 마법사(진동 시작신호·촬영·ML Kit OCR·확인·검증·승인), 음향교정기 보정, 기기 진단(JSON)
- AudioEngine: UNPROCESSED 우선, AGC/NS/AEC 해제 시도·기록, 무음 처리 감지(Android 10+), 클리핑 경고, 입력 경로 변경 감지, 100 ms 블록 기록
- 시험: core 37건, Robolectric 화면 7건
- 문서: docs/CALIBRATION_V2_REVIEW.md, V2 지시서 사본

### 변경
- "교정" → "보정" 표기 (공인 교정과 구분)
- Phase 1 초안의 "기준 소음계 단순 비교" 방식 제거 → V2 비교보정으로 대체

### 미구현
- 영상 녹화(MP4)·WAV 저장·오버레이 합성 (Phase 2)
- ARCore 거리, 레이저 거리계 (Phase 2)
- 스펙트로그램 (Phase 2로 이월)
- 자동 음원탐지 (Phase 3)
- PDF 보고서 (Phase 4)

## [Phase 0-A] 2026-11-10
### 추가
- Phase 0 문서: FEASIBILITY.md, HARDWARE_MATRIX.md, RISKS.md, TEST_PLAN.md, DEVELOPMENT_PLAN.md
- 관리 문서: README.md, DECISIONS.md, TEST_LOG.md, CHANGELOG.md
- 기준 지시서 사본: docs/spec/
