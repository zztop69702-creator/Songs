# CHANGELOG.md

## [0.4.0 — V3 클라우드 빌드·설치·데이터 보존 기반] 2026-11-10
### 추가
- GitHub Actions 워크플로(`.github/workflows/android-apk.yml`): 시험 → debug APK → (secrets 있으면) release APK → 버전·빌드번호 파일명, SHA-256, 서명지문 요약 → 아티팩트
- release 서명 설정(환경변수·secrets 전용), versionCode = 1000 + 빌드번호
- 데이터 백업·복원(ZIP, SHA-256 검증, 경로 검증, 덮어쓰기 금지)
- 앱 정보·백업 화면(버전, 빌드번호, 서명 인증서 SHA-256, 남은 공간)
- 측정 저장 시 저장공간 부족(50 MB 미만) 차단
- 시험: 백업 5건, 화면 2건(앱 정보, 큰 화면)
- 문서: docs/V3_PHASE0_REPORT.md, V3 지시서 사본
### 변경
- debug 빌드: 별도 앱 ID `.debug`, 이름 "영일 음향비전 (시험)"
- INTERNET 권한 제거(ML Kit 자동 병합분)
- Android 16 큰 화면 세로고정 opt-out 속성
- 화면 시험: 비동기 IO 대기 추가(안정화)

## [0.3.0 — 비적분형 소음계 연속판독 보정] 2026-11-10
### 추가
- core/calibration/SeriesComparison: 시간가중(S/F) 레벨 계산, 판독 시계열 1:1 비교, 표시 지연 추정, 오프셋(중앙값)·잔차·판정, 검증 오차
- app: 소음계 표시부 연속 촬영·OCR(가이드 상자, 0.4 s 간격, 프레임 사진 보존), 판독 검토(프레임별 제외), 보정 마법사에 방식 3종(연속판독·LAeq·간편)
- 시험: 연속판독 6건
### 수정
- 보정 마법사 단계 전환 시 스크롤 초기화

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
