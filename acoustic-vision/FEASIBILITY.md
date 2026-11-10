# FEASIBILITY.md — YOUNGIL Acoustic Vision Phase 0 기술 타당성 검토

- 작성일: 2026-11-10
- 기준 문서: `docs/spec/YOUNGIL_Acoustic_Vision_Claude_Code_SPEC.md` (V1.0, 첨부 docx와 내용 동일함을 확인)
- 상태: **Phase 0 문서 검토 단계. 앱 코드 미작성. 실기기 시험 0건.**

## 0. 표기 규칙

| 표기 | 의미 |
|---|---|
| **[확인]** | Google/AOSP/제조사 공개 문서에서 확인한 사실 (출처 명시). 단, 문서 확인일 기준이며 기기별 구현 편차는 별도 |
| **[계산]** | 물리식에 의한 이론 계산. 측정값 아님, 시뮬레이션도 아님 |
| **[추정]** | 일반적 경험·업계 통념에 근거한 판단. 근거 부족 |
| **[실기기 검증 필요]** | 실제 스마트폰·장비로 시험해야만 확정 가능 |

---

## 1. 결론 (먼저)

1. **Phase 1 스마트폰 MVP(카메라 + FFT + 참고 소음도 + 수동 마커 + 저장)는 기술적으로 실현 가능**하다. 핵심 API(AudioRecord, CameraX, Compose)는 모두 표준이며 위험은 "기기별 오디오 전처리"와 "타임스탬프 동기화" 두 가지에 집중된다. → **추진 권고**
2. **Phase 2 거리 측정(ARCore Depth)은 조건부**다. ARCore는 카메라를 점유하므로 CameraX 녹화와 동시에 쓰기 어렵고, 급식실의 스테인리스·무늬 없는 표면·수증기는 깊이 추정에 불리하다 [추정]. → **수동 입력을 기본, AR/레이저는 보조**로 설계 권고.
3. **Phase 3 자동 음원탐지는 원 지시서보다 더 강한 제약이 있다.** 급식실 송풍기의 주요 소음(회전·날개통과주파수, 모터 전자기음)은 대부분 **100~1,000 Hz** 대역인데, 손에 드는 크기(13~60 cm)의 마이크 배열로는 이 대역에서 **방향 분해능이 물리적으로 거의 없다** [계산, §5]. 자동탐지가 의미 있는 대역은 대략 **2 kHz 이상**(베어링, 벨트 슬립음, 공기 누설·틈새 소음, 고속 기류음)으로 한정된다.
   → AUTO 기능을 "급식실 송풍기 소음 위치 자동 탐지"로 홍보하면 **과장 표현**이 된다. **"고주파 소음원(2 kHz 이상) 방향 추정"으로 범위를 축소**하거나, 저주파는 **다지점 실측 스캔 맵(§7 대안 A)** 으로 대체할 것을 권고한다.
4. **소음도 값은 교정 전·후 모두 "참고값"으로만 표기**해야 한다. 스마트폰은 KS C IEC 61672-1 소음계가 아니며, 공인 측정 대체를 주장할 근거가 없다.
5. 사업성 관점: 영업 가치의 대부분은 **Phase 1~2 + Phase 4 보고서**(FFT로 BPF·모터음 식별 → 개선 전후 ΔL 비교 → PDF)에서 나온다. Phase 3은 투자 대비 회수가 가장 불확실하므로 **파일 기반 알고리즘 검증 통과 전 장비 구매 보류**(원 지시서 §8 원칙)에 동의한다.

---

## 2. 개발환경 점검

| 항목 | 결과 | 구분 |
|---|---|---|
| 이 작업 환경(클라우드 Linux 컨테이너) | OpenJDK 21.0.12, Gradle 8.14.3, Kotlin 2.0.21(Gradle 내장) 존재. **Android SDK / adb / Android Studio 없음** | [확인] (TEST_LOG.md T-0001) |
| 사용자 개발 PC(Windows) | 미점검. Android Studio·JDK·SDK·Gradle 버전 미확인 | [실기기 검증 필요] — TEST_PLAN P0-01 |
| 결론 | 이 컨테이너에서는 APK 빌드·기기 설치·실기기 시험 불가. Phase 1부터는 **사용자 Windows PC + 실제 Android 폰**이 필수이며, 컨테이너에서는 순수 Kotlin/JVM 단위시험(FFT, A가중 필터, BPF 계산, 데이터 모델)만 가능 | [확인] |

권장 기준(초안, 승인 시 확정): minSdk 26 이상 검토(근거: CameraX/ARCore/AudioRecord 타임스탬프 API 24+), targetSdk는 착수 시점 Google Play 요구 버전. **정확한 버전은 Phase 1 착수 시 공식 문서로 재확인**.

---

## 3. 오디오 입력 (소음도·FFT의 근간)

### 3.1 입력 전처리(AGC·노이즈 억제)
- **[확인]** Android는 원시 녹음용 `MediaRecorder.AudioSource.UNPROCESSED`(API 24)를 제공하며, 지원 여부는 `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)`로 확인한다. 미지원 시 `VOICE_RECOGNITION`이 대안이다.
- **[확인]** Android CDD 5.4: `VOICE_RECOGNITION` 소스는 기본적으로 노이즈 저감을 비활성화해야 한다(Android 12.1 CDD). AGC 비활성 조항은 Android 10 CDD에서 확인, **최신 CDD 문구는 재확인 필요**.
- **[확인]** `AutomaticGainControl`, `NoiseSuppressor`, `AcousticEchoCanceler` 클래스로 세션별 효과 존재 여부 확인·해제 가능.
- **[실기기 검증 필요]** 제조사가 CDD를 어느 정도 지키는지는 기기마다 다르다. 효과를 "해제했다"는 API 응답과 실제 신호 처리 여부는 별개 → **스윕 신호·정상음 레벨 스텝 시험(TEST_PLAN P0-04)으로 실제 확인**.

### 3.2 스마트폰 마이크 자체 한계 [추정 → 실기기 검증 필요]
- 내장 MEMS 마이크는 저역(대략 100 Hz 이하)에서 감도 저하·고역통과 필터가 흔하다 → 20~60 Hz 회전주파수 성분은 **과소평가 가능**.
- 고음압(급식실 송풍기 근접, 소음캐비닛 내부)에서 아날로그단 포화 가능 → 클리핑 검출 로직 필수.
- 기기 케이스·방향·손가림에 따라 응답이 달라짐 → 측정 자세 표준화 필요.

### 3.3 외장 측정 마이크
- 교정 파일(감도·주파수응답)이 개별 제공되는 USB 측정 마이크 사용 시 정확도 향상 기대 [추정]. Android USB OTG에서 인식 여부와 실제 샘플레이트는 **[실기기 검증 필요]**.
- 외장 마이크를 써도 **앱 + 폰 + 마이크 조합은 형식승인 소음계가 아님** → 표기 원칙 동일.

### 3.4 소음도 계산 가능 범위
- A가중 필터(IEC 61672 근사 IIR 또는 주파수영역 가중), LAeq/Lmax/Lmin(시간가중 F/S) 계산은 순수 연산이므로 구현 가능 [확인 가능: 단위시험으로 검증].
- dBFS → dB SPL 환산은 **교정 오프셋 없이는 불가**. 교정 전에는 "dBFS(상대)" 또는 "미교정 추정 dB(A)" 로만 표시.

---

## 4. 카메라·오디오 동시 취득과 동기화

### 4.1 권장 구조 (DECISIONS D-003 제안)
**CameraX 영상은 오디오 없이 녹화 + 앱이 AudioRecord로 원본 WAV를 직접 취득 → 저장 후 MP4에 오디오 트랙을 다중화(mux)**

이유:
- 분석용 오디오 소스를 `UNPROCESSED`로 직접 통제하고 원본 WAV를 보존할 수 있다(지시서 C항 "원본 보존" 충족).
- CameraX `Recorder`의 오디오와 분석용 `AudioRecord`를 동시에 여는 구조는 **동일 앱 내 동시 캡처 정책·기기 HAL에 따라 실패하거나 한쪽이 무음이 될 수 있다** [추정 → 실기기 검증 필요].
- 단점: 녹화 후 mux 처리(MediaCodec AAC 인코딩 + MediaMuxer) 구현 부담, 타임라인 오프셋 계산 필요.

### 4.2 타임스탬프
- **[확인]** `AudioRecord.getTimestamp(AudioTimestamp, TIMEBASE_MONOTONIC)`(API 24)로 프레임 위치 ↔ `System.nanoTime` 기준 시각 대응 가능.
- **[확인]** Camera2 `SENSOR_INFO_TIMESTAMP_SOURCE`는 `REALTIME`(elapsedRealtimeNanos 기준) 또는 `UNKNOWN`. MONOTONIC과 REALTIME은 기준이 다르므로(슬립 시간 포함 여부) **두 시계 간 오프셋 보정 필요**.
- **[실기기 검증 필요]** 실제 오차는 기기마다 다름 → **박수/LED 동시 신호 시험(TEST_PLAN P0-05)** 으로 측정. 목표안: ±1프레임(30 fps 기준 약 ±33 ms) 이내. 합격 기준은 승인 시 확정.

### 4.3 저장 영상에 오버레이 포함
- **[확인]** CameraX `androidx.camera.effects.OverlayEffect`가 카메라 프레임 위에 Canvas로 그리는 공식 효과 API로 존재(1.4 계열).
- **[확인: 개발자 포럼 보고]** PREVIEW+VIDEO_CAPTURE 동시 대상 시 미리보기에만 적용됐다는 보고, 다른 효과와 병용 시 카메라 실패 보고, 다수 비트맵 시 지연 보고가 있음 → **기기별 검증 필수**.
- 대안: (a) 원본 MP4는 오버레이 없이 보존 + 마커·수치는 JSON으로 저장 + 재생 시 오버레이 렌더 + 내보내기 시에만 합성(후처리). **원본 보존 원칙상 이 대안이 더 안전**하다고 판단 → DECISIONS D-004 제안.

---

## 5. 자동 음원탐지(Phase 3) 물리적 한계 — 핵심 검토

### 5.1 회전·날개통과주파수 예시 [계산]
| 설비 예(가정값) | N(rpm) | Z(날개) | 회전주파수 N/60 | BPF N×Z/60 |
|---|---|---|---|---|
| 시로코(다익) 배기팬 | 1,450 | 40 | 24.2 Hz | 966.7 Hz |
| 축류팬 | 1,750 | 6 | 29.2 Hz | 175.0 Hz |
| 원심팬 | 1,150 | 10 | 19.2 Hz | 191.7 Hz |
| 2극 모터 전자기음(60 Hz 전원) | — | — | — | 약 120 Hz 및 고조파 [추정] |

※ rpm·날개 수는 예시 가정값이며 실제 설비 명판·실측으로 대체해야 함.

### 5.2 배열 크기 대비 방향 분해능 [계산]
조건: 음속 343 m/s, 원거리 근사, 일반 지연합(delay-and-sum) 빔포밍, 주엽 반폭 ≈ asin(λ/D) (첫 영점 기준 단순 근사). 공간 앨리어싱 방지 간격 d ≤ λ/2.

| 주파수 | 파장 λ | 앨리어싱 없는 최대 간격 | D=0.126 m (16채널 4×4, 간격 42 mm 가정) | D=0.3 m | D=0.6 m |
|---|---|---|---|---|---|
| 125 Hz | 2.74 m | 1,372 mm | 분해 불가 | 분해 불가 | 분해 불가 |
| 250 Hz | 1.37 m | 686 mm | 분해 불가 | 분해 불가 | 분해 불가 |
| 500 Hz | 0.69 m | 343 mm | 분해 불가 | 분해 불가 | 분해 불가 |
| 1 kHz | 0.34 m | 172 mm | 분해 불가 | 분해 불가 | ±35° |
| 2 kHz | 0.17 m | 86 mm | 분해 불가 | ±35° | ±17° |
| 4 kHz | 0.09 m | 43 mm | ±43° | ±17° | ±8° |
| 8 kHz | 0.04 m | 21 mm | ±20° (단, 42 mm 간격은 약 4.1 kHz 이상 앨리어싱) | ±8° | ±4° |

해석:
- 3 m 거리에서 ±17°는 좌우 약 ±0.9 m 범위 → 급식실 덕트·팬 하우징 구분에 겨우 쓸 만한 수준.
- **송풍기 BPF(175~970 Hz)는 60 cm 배열로도 방향 구분이 사실상 불가**. CLEAN-SC, DAMAS, MUSIC 등 고분해능 기법은 개선 여지가 있으나 SNR·모델 오차에 민감하며 물리 한계를 "공짜로" 넘지 못함 [추정].
- 상용 음향카메라가 주로 수 kHz 이상 대역을 다루는 이유와 같다 [추정 — 제품별 사양 확인 필요].
- 협대역 순음(BPF)은 GCC-PHAT 상관 피크가 주기적으로 반복되어 **방향 모호성**이 생김 → 지시서 D항 "협대역 소음 제한 표시"는 필수.
- 급식실은 타일·스테인리스로 반사가 강함 → 반사음이 허상 음원을 만듦 [추정 → Phase 5 검증].

### 5.3 Android에서 다채널 원시 입력
- **[확인]** 2채널 초과 USB 입력은 `AudioFormat.Builder.setChannelIndexMask()`로 요청해야 하며, 채널 마스크를 함께 설정하면 2채널로 제한된다(AOSP Automotive 문서).
- **[실기기 검증 필요]** 폰의 오디오 HAL·USB 정책이 실제로 4/8/16채널을 앱에 전달하는지는 **기기별로 다르며 공개 문서로 보장되지 않음**. 많은 폰이 USB 입력을 2채널로 다운믹스/제한할 가능성 [추정].
- 우회안: (1) 사용자 공간 USB 드라이버(UsbManager + 직접 UAC 처리) — 개발 난이도 높음, (2) 배열 측 보드(라즈베리파이·XMOS 등)에서 동시 샘플링 후 네트워크/USB로 전송, (3) **현장 녹음 후 PC 오프라인 처리**(Phase 3 알고리즘 검증용으로 가장 현실적).

---

## 6. 거리 측정(ARCore Depth)

- **[확인]** Depth API는 별도 ToF 센서 없이 동작하지만 **모든 ARCore 지원 기기가 Depth를 지원하지는 않으며**, 런타임에 `Session.isDepthModeSupported()`로 확인해야 한다. 지원 기기 목록은 Google 공식 페이지에서 확인.
- **[확인]** 일반 Depth 이미지의 유효 범위는 대략 20~30 m 수준이며 거리가 멀수록 밀도·정확도 저하. Raw Depth API는 신뢰도 이미지 제공.
- **[확인]** ARCore Shared Camera로 Camera2와 공유 가능하나 제약(`setRepeatingRequest` 불가 등)이 있음. ARCore Recording API의 깊이 영상은 "미리보기 용도"로 명시됨.
- **[추정]** CameraX 녹화와 ARCore 세션 동시 운용은 어렵다 → **"거리 측정 모드"를 녹화와 분리**(녹화 일시정지 → AR로 대상점 거리 측정 → 결과를 측정 레코드에 출처와 함께 첨부).
- **[추정]** 급식실 스테인리스 덕트·후드, 무늬 없는 벽, 수증기·조명 반사는 깊이 추정 품질을 떨어뜨림 → 신뢰도 낮으면 값을 저장하지 않거나 "저신뢰" 표기.
- 원칙: 표면거리 ≠ 음원거리. 측정 실패 시 값 생성 금지(지시서 E항) — 데이터 모델에서 `distance = null` + 실패 사유 저장.

---

## 7. 대안 및 권고 (독립적 판단)

| 구분 | 내용 | 장점 | 한계 |
|---|---|---|---|
| **대안 A: 다지점 실측 스캔 맵** (신규 제안) | 단일(교정된) 마이크로 설비 주변 격자 지점을 순서대로 측정, 각 지점을 영상/도면에 수동 지정 → 지점별 LAeq·BPF 레벨을 색상 맵으로 표시 | 저주파에도 유효, 장비 추가 거의 없음, **"실측값"으로 표기 가능**, 근접 측정으로 반사 영향 감소 | 자동이 아님, 측정 시간 소요, 운전조건 일정 유지 필요 |
| 대안 B: AUTO 범위 축소 | Phase 3 AUTO를 2 kHz 이상 고주파 음원 방향 추정으로 한정 표기 | 물리적으로 정직, 베어링·누설 진단에 유용 | 송풍기 BPF 위치 탐지 불가 |
| 대안 C: 오프라인 처리 | 다채널 레코더로 현장 녹음 → PC에서 빔포밍 → 결과를 앱/보고서로 가져오기 | Android 다채널 제약 회피, 알고리즘 검증 빠름 | 실시간 아님 |
| 대안 D: 외주/임대 | 상용 음향카메라가 필요한 소수 현장만 임대 또는 전문업체 협업 | 초기 투자 최소 | 비용·일정 외부 의존, **임대 가격 미확인** |

**권고 순서:** Phase 1 → Phase 2(수동 거리 기본) → **대안 A를 Phase 2.5로 추가** → Phase 4 보고서 → Phase 3은 대안 C로 파일 기반 검증 후 장비 구매 여부 재결정.

---

## 8. 법적·윤리 검토 포인트 (전문가 확인 필요)

1. **측정기기 표기**: 「환경분야 시험·검사 등에 관한 법률」은 환경부령으로 정한 측정기기에 형식승인(제9조)·정도검사(제11조)를 규정 [확인]. **소음계가 형식승인 대상 품목인지 시행규칙 별표 원문 확인 필요**. 어느 경우든 앱 결과를 법정 측정값처럼 표기하면 안 됨.
2. **측정대행업 해당 여부**: 법정 소음측정(배출시설 자가측정 등)을 대행하는 경우 측정대행업 등록이 필요할 수 있음 → 영일 서비스가 "설비 개선 진단 참고자료"인지 "법정 측정"인지 계약서·보고서에 명확히 구분. **법령 원문 확인 필요**.
3. **개인정보**: 급식실 영상에 조리종사자·학생 얼굴이 찍힐 수 있음 → 촬영 전 학교 승인, 인물 촬영 최소화, 필요 시 모자이크, 보관기간 설정(「개인정보 보호법」). 앱에 촬영 동의·안내 체크 항목 권고.
4. **공공계약 투명성**: 보고서에 실제 측정 수행자·사용 장비·교정 상태·계약 주체(영일)를 명시.

---

## 9. 출처 (확인일 2026-11-10)
- ARCore Depth 개발자 가이드: https://developers.google.com/ar/develop/java/depth/developer-guide
- ARCore Geospatial Depth(범위 관련): https://developers.google.com/ar/develop/c/depth/geospatial-depth
- ARCore Raw Depth: https://developers.google.com/ar/develop/unity-arf/depth/raw-depth
- ARCore Shared Camera: https://developers.google.com/ar/reference/java/com/google/ar/core/SharedCamera
- ARCore Recording & Playback: https://developers.google.com/ar/develop/recording-and-playback
- CameraX OverlayEffect: https://developer.android.com/reference/androidx/camera/effects/OverlayEffect
- OverlayEffect 관련 이슈: https://www.github.com/android/camera-samples/issues/571 , https://groups.google.com/a/android.com/g/camerax-developers/c/RpUDeRG3AQE , https://groups.google.com/a/android.com/g/camerax-developers/c/cnOCKEx5S6E
- Android CDD 5.4 Audio Recording (12.1): https://android.googlesource.com/platform/compatibility/cdd/+show/refs/tags/android-platform-12.1.0_r10/5_multimedia/5_4_audio-recording.md
- Android CDD 5.4 (Android 10): https://android.googlesource.com/platform/compatibility/cdd/+/refs/heads/android10-dev/5_multimedia/5_4_audio-recording.md
- AudioFormat (channel index mask): https://developer.android.google.cn/reference/android/media/AudioFormat
- AOSP Automotive 마이크 입력(다채널 예): https://source.android.com/docs/automotive/audio/microphone-input
- ReSpeaker USB 4-Mic Array v2.0: https://wiki.seeedstudio.com/ReSpeaker_Mic_Array_v2.0
- miniDSP UMA-16 v2(판매처 자료): https://www.audiophonics.fr/en/measurement-microphones/minidsp-uma-16-v2-camera-p-21306.html
- 환경분야 시험·검사 등에 관한 법률(요약): https://wikidocs.net/31844 , KTR 정도검사 안내: https://www.ktr.or.kr/certification/envMeasure/contentsid/2740/index.do
