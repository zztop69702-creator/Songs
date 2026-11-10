# TEST_PLAN.md — 시험 계획

- 작성일: 2026-11-10
- 원칙: **실제 장비 없이 실행한 시뮬레이션·단위시험은 "실측 검증"으로 기록하지 않는다.** 결과 구분: `UNIT`(코드 단위시험) / `SIM`(합성신호·시뮬레이션) / `DEVICE`(실기기) / `FIELD`(현장)
- 모든 결과는 TEST_LOG.md에 날짜·기기·OS·명령·결과·판정으로 기록.
- 합격 기준 중 "(안)"은 승인 시 확정.

## Phase 0 — 기술검증 (사용자 PC·실기기 필요)

| ID | 시험 | 방법 | 기록 항목 | 합격(안) | 유형 |
|---|---|---|---|---|---|
| P0-01 | 개발환경 점검 | Windows에서 `java -version`, Android Studio 버전, `sdkmanager --list_installed`, `adb version`, `adb devices` | 각 버전 | Android Studio 최신 안정판, JDK 17 이상, 기기 인식 | DEVICE |
| P0-02 | 기기 기본정보 | `adb shell getprop ro.product.model`, `ro.build.version.sdk`, `ro.build.version.release` | 모델·OS | 기록 완료 | DEVICE |
| P0-03 | 카메라+오디오 동시 취득 | 진단(Probe) 앱: CameraX 미리보기/녹화 중 AudioRecord 동시 동작 | 실패/무음/정상 | 30초간 양쪽 정상 | DEVICE |
| P0-04 | 오디오 전처리 확인 | UNPROCESSED 지원 여부, AGC/NS 효과 존재·해제, 스피커 1 kHz 순음을 6 dB씩 스텝 변화 → 앱 dBFS 변화량 | 스텝별 레벨 | 각 스텝 6±1 dB 추종(안), 클리핑 레벨 기록 | DEVICE |
| P0-05 | 타임스탬프·동기 | 화면 LED 점멸+짧은 비프를 동시에 녹화, 영상 프레임 vs WAV 펄스 위치 비교 | 오프셋(ms) 10회 | 평균 ±33 ms 이내(안), 편차 기록 | DEVICE |
| P0-06 | 오버레이 녹화 | OverlayEffect로 텍스트·점선 원 녹화본 포함 여부 | 포함 여부, fps | 녹화본 포함 + 프레임 저하 10% 이내(안) | DEVICE |
| P0-07 | 소음계 비교(예비) | 기준 소음계와 나란히 핑크노이즈/팬 소음 측정 | 앱 dBFS, 소음계 dB(A) | 오프셋·편차 기록(합격 기준 없음, 데이터 수집) | DEVICE |
| P0-08 | USB 다채널 입력 | 외장 다채널 장치 연결 → `getDevices()` 채널 수, index mask 4/6/8/16ch AudioRecord 생성, 채널별 손가락 탭으로 독립성 확인 | 노출 채널 수, 실제 분리 여부 | 원시 4ch 이상 분리 확인 시 "가능" | DEVICE |
| P0-09 | ARCore Depth | 지원 여부, 1/2/3/5 m 줄자 기준 평면(벽·스테인리스) 거리 비교, CameraX 동시 사용 여부 | 오차, 실패율 | 오차 기록(합격 기준은 Phase 2에서) | DEVICE |
| P0-10 | 장시간 안정성 | 카메라+FFT 30분 | 발열·크래시·fps | 크래시 0회 | DEVICE |

> P0-03~P0-10은 앱 코드가 필요하다. 이를 위해 **Phase 0-B "기기 진단(Probe) 앱"**(결과를 JSON으로 내보내는 최소 앱) 작성 승인을 요청한다. Probe 앱은 제품 코드가 아니며 측정 기능을 제공하지 않는다.

## Phase 1 — 스마트폰 MVP

| ID | 시험 | 유형 | 합격(안) |
|---|---|---|---|
| P1-01 | FFT 정확도: 합성 사인파(100 Hz, 1 kHz, 5 kHz)로 피크 주파수 오차 ≤ 1 bin | UNIT/SIM | 통과 |
| P1-02 | A가중 필터 응답: IEC 61672 표 값과 주파수별 비교(Class 1 허용오차 참고) | UNIT/SIM | 표 기준 이내 |
| P1-03 | LAeq/Lmax/Lmin 계산: 알려진 신호 수치 일치 | UNIT/SIM | ±0.1 dB |
| P1-04 | 실제 입력으로 FFT 변화(지시서 인수) | DEVICE | 순음 변경 시 피크 추종 |
| P1-05 | BPF 계산 f=N×Z/60 및 피크 매칭 표시 | UNIT | 수식 일치 |
| P1-06 | 수동 마커 저장·재표시, 출처=manual 저장 | DEVICE | 앱 재시작 후 동일 |
| P1-07 | 권한 거부·중단(전화 수신, 백그라운드 전환) 처리 | DEVICE | 크래시 없음, 안내 표시 |
| P1-08 | 미교정 상태 표기 확인 | DEVICE | "미교정·참고값" 항상 표시 |

## Phase 2 ~ 5 (요약, 해당 단계 착수 시 상세화)
- Phase 2: 녹화-분석 시점 동기(P0-05 재시험), WAV/CSV/JSON 원본 일치, 거리 출처 기록, Depth 미지원 기기 오류 처리.
- Phase 2.5(제안): 다지점 스캔 맵 — 동일 지점 반복 측정 재현성(±1 dB(안)).
- Phase 3: 합성 다채널 WAV(SIM) → 녹음된 다채널 WAV(DEVICE) → 실시간(DEVICE) 순. 기준 음원 각도 대비 오차를 **주파수 대역별로** 기록. SIM 결과는 실측으로 기록 금지.
- Phase 4: 보고서 수치 ↔ 원본 JSON 자동 대조 스크립트, 누락 항목 명시 확인.
- Phase 5: 기준 소음계 비교(여러 현장), 반사 환경, 장시간 안정성. 공식 계측기 대체 주장 금지.
