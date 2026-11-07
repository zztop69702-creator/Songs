# 경험곱하기AI `src/app.html` 지도

한 파일 안에 CSS → HTML → JS 순서로 들어 있다. 고칠 곳을 찾을 때 `grep -n` 으로 아래 이름을 찾는다.

## 목차
1. 화면 구성
2. 데이터 표 (계산의 바탕)
3. 상태 S (localStorage)
4. 계산 규칙
5. 화면 함수
6. 단계별 화면 ENTER[0..9]
7. 고칠 때 자주 쓰는 지점

## 1. 화면 구성
- **상단 바**: 로고, `경험곱하기AI v1.8 빌드 MMDD-HHMM`, 진행 단계 목록(1~10), 'PC 설치 파일 받기'(온라인판만), '글자 크게'.
- **1쪽(왼쪽, 고정) = 지도앱** `.map-page`
  - `#mapHead`: 제목, '1~30 차례로 쌓기'(`#mapPlay`), '지도 비우기'(`#mapReset`), '움직임 끄기/켜기'(`#motionBtn`), '내 지도로'(`#mapClose`, 전체 지도 중), '내 목표 보기'(`#mapGoals`, 탐색 중).
  - `#jobs.grid30`: 전문가 30명 (분야 6 × 5). 칸 `.job.mini[data-job][data-slot]`, ★ 경력 추천 순위, ☆ 추가 추천, ①②③ 목표.
  - `#stage` (어두운 캔버스, 시안 A '빛줄기 연결도'): `#dream`(왼쪽 위 문구) · `#hub`(경험 × AI 빛기둥, `#hubText`) · `#sources`(출발점 최대 3) · `#skills`(역량 14칸, 가로로 차오름) · `#plusGate`/`#minusGate`(도움·돌발, 모든 단계에서 누름) · `svg#wires`(빛줄기).
  - `#info` 설명 상자(항상), `#mapInfo` > `#myInfo`(반영된 조건 n/8) · `#demoInfo`(전체 지도 합계).
- **2쪽(오른쪽) = 질문·답변** `.flow-page` 의 `section[data-step=0..9]`, 아래 `#nav`(이전/다음).
- 10단계: `#summary`(결론 요약, 먼저 자동) ↔ `#fullDoc`(카드 `#card`, 학습 체크 `#study`, 지침문 `#prompt`).

## 2. 데이터 표
| 이름 | 내용 | 주의 |
|---|---|---|
| `SKILLS` (14) | AI 리터러시 … 소유 증명. 8~13번은 v1.8 추가 | **번호를 바꾸지 말 것**(저장된 자가진단 S.self 키) |
| `SKILL_D`, `SKILL_Q`, `PHRASE`, `SKILL_CAN` | 역량별 설명·자가진단 질문·정의 문구·'AI ○○이/가' | 길이 = SKILLS (console.assert) |
| `JOBS` (30) | 전문가 n, w, s(역량 번호). 앞 7명은 기획안 62쪽 | 앞 7명의 기존 8역량 연결은 `MAX7` 검사로 고정 |
| `NEW_LINKS`, `VERIFY` | 새 역량 연결 초안과 AI 추론 검증 기록(근거) | 연결 바꾸면 VERIFY에 근거 추가, 보고서 재생성 |
| `CATS`, `SHORT`, `NUM`, `CAT` | 분야 6 × 5, 짧은 이름, 1쪽 번호, 분야 번호 | |
| `GROUPS` (4) | 현장시공·기술·영업·관리: j(추천 3), s(우선 역량), a, w(조건 문구) | |
| `PAIRS`, `EXP`, `STAGES`, `FIT` | 두 직군 추천, 경험 유형 5, 일하는 방식 4단계(MBTI형), 성향별 가점 | 기획안 7명만 참조 |
| `GROUP_CATS`, `EXP_SKILLS`, `STYLE_SKILLS` | 추가 추천(☆) 점수용 | |
| `CONDITIONS` (6), `PLUS` (4), `MINUS` (5), `CUT` | 적용 조건, 도움·돌발, 돌발별 끊김 역량 | |
| `ROLE`, `AIM` (30) | 카드 역할·목표 | 길이 30 검사 |
| `LEVELS`, `HOURS`=36, `KEY` | 판단 3단계, 기준 시간, 저장 키 'experience-ai-map-v1' | KEY 바꾸면 사용자 입력값 사라짐 |

## 3. 상태 S (`blank()`)
step, name, age, sex, status('c' 재직/'r' 퇴직), groups(최대 4, 첫째=주 경력), exp, expType, sides[4], choice(=goals[0]), goals(1~3순위), lastChoice, picks(1쪽 관심 최대 3), self{역량:1~5}, checks, plus, minus, model(5요소 수정), done(학습 체크).
`check()` 가 깨진·옛 저장값을 정리하고, 빠진 답이 있는 단계로 되돌린다(입력값은 지우지 않음).
화면 상태(저장 안 함): `full`(전체 지도 시연), `mapFree`(지도 비우기 뒤 탐색), `replaying`, `motionPref`('experience-ai-motion').

## 4. 계산 규칙 (바꾸면 사용자 승인 필요)
- `baseRows(job|jobs, groups)`: 역량 점수 = 직업 필요 2(여럿이면 합산) + 주 경력 우선 2 + 강점마다 1, 도움·돌발 보정(plus1 경험구조화−1, plus2 활용공유−1, plus3 개별에이전트+1·성과비용+1, minus0 윤리보안+2, minus2 결과검증+2, minus3 도구제작−1) → `spread()` 5% 단위 합 100.
- `personalRows`: 가중치 × max(0.5, 5−자가진단) (미응답 3점, '매우 그렇다'도 최소 유지 시간).
- `finalLevel`: 조건 수·허용 여부 → 0~2, 시범지원 +1, 담당자 교체 −1, 보안 강화는 0 고정.
- 추천: `recommended`(직군 1~2개는 표, 3~4개는 결합 합산) → `ranked`(경험·성향 가점) + `extraRecs`(☆ 새 23명 중 2명) → `candidates`(+ 1쪽 picks).
- 카드 숫자는 **1순위 목표 기준**. 1쪽 picks 는 빛줄기·비교표에만.
- 시간: 36시간(업무 시간 부족이면 18).

## 5. 화면 함수
`fitScreen`(앱 전체 85~180% 확대, `--z`) · `fitMap`(1쪽이 넘치면 표+캔버스 80%까지) · `sourceList`/`renderSources` · `drawWires`(곡선 빛줄기 `pairWire`, 빛기둥 점) · `paint`/`paintMine`(칸 채움·색, 조건 칩, ★☆①, 예측 문구 `dreamHtml`) · `fly`(점 이동, 움직임 꺼짐이면 즉시) · `flowFrom`(전문가 클릭) · `playConclusion`(10단계 재생) · `rebuildMap`(내 목표 보기) · 실시간 흐름 `setInterval`(2초) · `playMap`/`addMapJob`/`paintFull`(1~30 시연).

## 6. 단계 ENTER[i] (화면 번호 = i+1)
0 시작 · 1 경력(최대 4) · 2 경험 · 3 일하는 방식 · 4 해 볼 일(1~3순위, `#jobChoices`) · 5 자가진단 · 6 적용 조건 · 7 도움·돌발 · 8 역할 다듬기(5요소) · 9 내 카드(요약 → 문서). `need()` 가 다음으로 넘어갈 조건, `show()` 가 단계 전환.

## 7. 자주 쓰는 지점
- 문구만 바꾸기: 해당 문자열을 `grep -n` → 정확히 1곳인지 확인 후 교체.
- 새 불변 조건: 데이터 표 바로 아래 `console.assert(...)` 추가 → 검사 스크립트가 'assert' 를 오류로 잡는다.
- 맨 위 선언 순서: 위쪽 표에서 아래쪽 `const`(예: HOURS)를 쓰면 TDZ 오류로 앱이 멈춘다.
