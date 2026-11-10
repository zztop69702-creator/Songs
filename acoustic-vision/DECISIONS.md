# DECISIONS.md — 의사결정 기록

상태: **확정**(SPEC 또는 사용자 승인) / **제안**(승인 대기) / **보류**

| ID | 결정 | 상태 | 근거 |
|---|---|---|---|
| D-001 | Kotlin + Jetpack Compose Android 네이티브 앱 | 확정 | SPEC §1 |
| D-002 | 음원 위치는 HYBRID(AUTO+MANUAL), 배열 미연결 시 AUTO 비활성 | 확정 | SPEC §1, §2-D |
| D-003 | 실측/추정/수동/시뮬레이션을 데이터·UI에서 구분 (`Provenance` 필수 필드) | 확정 | SPEC §1, §6 |
| D-004 | minSdk 29 (Android 10) | **제안** | 오디오 캡처 동시성 정책·Scoped Storage 단순화. 단점: Android 9 이하 기기 제외 |
| D-005 | 프로젝트 위치: 저장소 `Songs`의 `acoustic-vision/` 하위 폴더 | **제안** | 현재 저장소에 다른 프로젝트(bizcard-laptop) 공존. 장기적으로 별도 저장소 분리 권장 |
| D-006 | 녹화는 무음 영상(CameraX) + 별도 AudioRecord WAV, 타임스탬프 정렬 | **제안** | 측정 오디오 원본 보존, CameraX 오디오는 압축·전처리 경로 (FEASIBILITY §4) |
| D-007 | 저주파(약 ≤1~2 kHz) 위치 판별은 "근접 스캔 맵"으로, AUTO는 고주파 대역 한정 | **대체됨 → D-012** | 배열 분해능 계산 (FEASIBILITY §6) |
| D-008 | Phase 4(보고서)를 Phase 3(AUTO)보다 먼저 수행 | **제안** | 영업 가치 우선, 고불확실성 작업 분리 |
| D-009 | 오버레이는 원본+메타데이터 저장 후 후처리 렌더링을 기본으로, OverlayEffect 실시간 굽기는 선택 | **제안** | OverlayEffect 녹화 적용 문제 보고 (FEASIBILITY §4.3) |
| D-010 | ARCore 거리 측정은 녹화와 분리된 별도 모드 | **제안** | ARCore Shared Camera는 Camera2 기반, CameraX 동시 사용 불가 |
| D-011 | Phase 3B 다채널 처리는 PC 파일 검증 → Android 직결 시험 → 실패 시 보조 처리기 | **제안** | Android USB 다채널 입력 불확실 (R-02) |
| D-012 | **Phase 3 재편:** 3A = AR 스캔 히트맵(스마트폰 + 외장 교정 마이크 1개, ARCore 위치 추적)을 자동 위치 표시의 기본으로 함. 3B = 대형 배열(1.5~2 m, 8~16ch, 노트북 연산)은 선택 확장. 소형 배열 AUTO는 약 2 kHz 이상 성분에만 적용 | **확정 (사용자 승인 2026-11-10)** | 저주파 분해능 한계 (FEASIBILITY §6.2) → 근접 측정으로 해결 (FEASIBILITY §6.7) |
| D-013 | 히트맵 데이터 출처 구분: 스캔 지점 레벨 = `MEASURED`, 지점 간 보간값 = `ESTIMATED`, 미스캔 영역 = 값 없음("미측정" 표시) | **확정 (D-012에 따름)** | SPEC §1 핵심 원칙, §2-D 임의 결과 생성 금지 |
