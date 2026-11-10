# TEST_LOG.md — 시험 기록

구분: **실측**(실제 기기/장비) / **시뮬레이션**(합성신호·이론계산) / **환경점검**

| 일시 | ID | 구분 | 환경 | 내용 | 결과 |
|---|---|---|---|---|---|
| 2026-11-10 | ENV-01 | 환경점검 | Claude Code 클라우드 컨테이너 (Linux x86_64) | `java -version`, `which gradle adb sdkmanager`, `echo $ANDROID_HOME` | OpenJDK 21.0.12 있음, Gradle 있음, **Android SDK·adb 없음, 실기기 없음** → APK 빌드/실기기 시험 불가 |
| 2026-11-10 | CALC-01 | 시뮬레이션(이론계산) | Python 3 | 파장·λ/2 간격·선형배열 빔폭(0.886λ/D)·FFT 분해능·GCC-PHAT 1샘플 각도 계산 | FEASIBILITY §3.1, §6 표에 반영. **실측 아님** |

## Phase 0 실기기 시험
- 수행한 실기기 시험: **없음**.
- T-01 ~ T-09는 Phase 1 착수 시 사용자 Windows PC·Android 기기에서 수행 예정.
