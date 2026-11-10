# YOUNGIL Acoustic Vision

학교 급식실 송풍기·모터·덕트·소음캐비닛의 소음도, 주파수, 음원 위치, 거리, 영상 기록을 통합하는 Android 현장 진단 앱 (개발 중).

> **현재 상태: Phase 0 기술검증 문서 단계 — 앱 코드 없음, 실기기 시험 0건. Phase 1 착수 전 승인 대기.**
> 본 앱의 소음도 값은 참고값이며 공인 소음계 측정을 대체하지 않습니다.

## 문서
| 문서 | 내용 |
|---|---|
| [FEASIBILITY.md](FEASIBILITY.md) | 기술 타당성 결론, 물리적 한계, 대안, 법적 검토 포인트 |
| [HARDWARE_MATRIX.md](HARDWARE_MATRIX.md) | 폰·마이크·배열 후보와 구매 전 확인 항목 |
| [RISKS.md](RISKS.md) | 위험 목록과 완화책 |
| [TEST_PLAN.md](TEST_PLAN.md) | Phase 0~5 시험 계획 |
| [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) | 단계 계획, 모듈 구조, 데이터 모델 초안 |
| [DECISIONS.md](DECISIONS.md) | 의사결정 기록(확정/제안) |
| [TEST_LOG.md](TEST_LOG.md) | 시험 기록 |
| [CHANGELOG.md](CHANGELOG.md) | 변경 이력 |
| [docs/spec/](docs/spec/) | 기준 개발 지시서 V1.0 |

## 표기 원칙
실측 / 추정 / 수동 입력 / 시뮬레이션을 화면·저장 데이터·문서에서 항상 구분합니다.
마커 출처(auto/manual/expert-edited), 거리 출처(AR/외장/수동), 교정 상태는 필수 필드입니다.

## 서비스 제공 주체
진단 서비스 및 계약 주체: 영일 (보고서에 측정 수행자·장비·교정 상태 명시)
