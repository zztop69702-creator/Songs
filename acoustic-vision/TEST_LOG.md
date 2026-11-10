# TEST_LOG.md — 시험 기록

유형: `UNIT` 단위시험 / `SIM` 합성신호·시뮬레이션 / `CALC` 이론 계산 / `ENV` 환경 점검 / `DEVICE` 실기기 / `FIELD` 현장
**SIM·CALC 결과는 실측 검증이 아니다.**

| ID | 날짜 | 유형 | 환경 | 명령/방법 | 결과 | 판정 |
|---|---|---|---|---|---|---|
| T-0001 | 2026-11-10 | ENV | 클라우드 Linux 컨테이너(x86_64) | `java -version`, `gradle --version`, `command -v sdkmanager adb kotlinc` | OpenJDK 21.0.12 / Gradle 8.14.3 / Kotlin 2.0.21(Gradle 내장) 있음. sdkmanager·adb·kotlinc 없음, ANDROID_HOME 미설정 | 이 환경에서 APK 빌드·실기기 시험 불가 (사용자 Windows PC 필요) |
| T-0002 | 2026-11-10 | CALC | Python 3.13 | 음속 343 m/s, 주엽 반폭 asin(λ/D), 앨리어싱 c/(2d) 계산 | FEASIBILITY §5.2 표 (예: D=0.6 m, 2 kHz → ±17°; 1 kHz 미만 분해 불가) | 이론값. 실측 아님 |
| T-0003 | 2026-11-10 | CALC | Python 3.13 | f=N/60, f_BPF=N×Z/60 (가정값 3건) | 1450rpm·40날개 → 24.2 / 966.7 Hz 등 | 이론값. 실제 설비값 아님 |

실기기 시험: **0건** (P0-01 ~ P0-10 미실시)
