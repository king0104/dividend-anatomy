# TTM 창 경계 정의 변경 — "양 끝 포함"에서 "시작점 제외, 끝만 포함"으로

## 배경

`docs/specs/yield-change-decomposition.md`가 애초에 정한 TTM 창 정의는
`[t-12개월, t]`(양 끝 포함)이었다. 이 정의 자체가 만들 수 있는 문제를
`docs/ai-defects/04-ttm-window-boundary-overlap.md`에서 이미 한 번
겪었지만("정확히 1년 차이 나는 두 지급일이 인접한 두 창에 모두 걸림"),
그때는 테스트 데이터의 날짜를 하루 옮겨서 우회하고 프로덕션 코드는
그대로 뒀다 — 문서에 "실제 데이터에서도 이 상황이 나올 수 있다"고
위험을 남겨두고 의도적으로 보류한 상태였다.

## 실제로 터진 사건

삭감 이력 탐지 지표(`DividendCutDetectionService`)를 실제 KO 데이터로
검증하는 중 `GET /api/tickers/KO/dividend-cuts`가 400을 반환했다:

```json
{"message": "foundCount(5)는 0 이상 expectedCount(4) 이하여야 한다"}
```

원인 확인: KO의 2004-03-11 지급과 2005-03-11 지급이 **정확히 12개월
차이**다. `windowEnd=2005-03-11`인 창(`[2004-03-11, 2005-03-11]`, 양
끝 포함)에 2004-03-11, 2004-06-14, 2004-09-13, 2004-11-29,
2005-03-11 **5건**이 다 걸려서 `foundCount(5) > expectedCount(4)`.

이건 희귀 케이스가 아니다. 삭감 이력 탐지는 **모든 정기 배당 지급일을
차례로 창 끝(`windowEnd`)으로 써서 훑기 때문에**, 20년 넘게 분기 배당을
낸 종목이면 어딘가에서 이런 정확한 12개월 정렬이 나올 확률이 높다 —
(반면 배당수익률 기여도 분해는 `t1`을 사용자가 임의로 고른 단일 시점만
쓰기 때문에 이 경계에 정확히 걸릴 확률이 낮았다. 같은 근본 위험이
지표에 따라 노출되는 빈도가 다르다는 뜻.)

## 결정

TTM 창을 **시작점 제외, 끝만 포함**(`(t-12개월, t]`)으로 바꾼다.

- `t0` 창과 `t1`(=`t0`+1년) 창이 인접할 때, `t0` 그 날짜 지급은 `t0` 창의
  끝(포함)에만 걸리고 `t1` 창의 시작(제외)에는 안 걸린다 — 이중 계산 자체가
  구조적으로 불가능해진다.
- 대안(`foundCount > expectedCount` 허용하도록 불변식만 완화)도 검토했으나,
  창 정의 자체를 명확히 하는 쪽이 "완전함(`foundCount == expectedCount`)"의
  의미를 그대로 지킬 수 있어서 이쪽을 선택했다.

## 바뀐 것

- `DividendPaymentRepository`: `findByTickerAndTypeAndExDividendDateBetweenOrderByExDividendDateAsc`
  (양 끝 포함) → `findByTickerAndTypeAndExDividendDateAfterAndExDividendDateLessThanEqualOrderByExDividendDateAsc`
  (시작 제외, 끝 포함).
- `TtmDividendAggregationService.summarize()` — 위 메서드 호출로 변경.
- `docs/specs/yield-change-decomposition.md` 1.2절 — 창 정의 문구 수정.
- 기존 `YieldDecompositionServiceTest`의 "경계를 피하려고 날짜를 하루
  옮긴" 임시방편(`docs/ai-defects/04` 참고)이 이제 구조적으로 불필요해짐 —
  `t0` 그 날짜를 그대로 써도 이중 계산이 안 일어난다는 걸 테스트로 재확인.

## 영향받지 않는 것

- 분할 조정 로직(`splitAdjustedAmount`)은 창 정의와 무관 — 그대로.
- 반올림·기여도 분해 계산식 자체는 변경 없음.

## 추가 발견 (2026-08-23) — 창 경계 수정만으로는 불충분했음

위 수정을 배포하고 KO 실데이터로 다시 검증했더니 `GET
/api/tickers/KO/dividend-cuts`가 여전히 400을 반환했다. KO의 88개
정기 배당 지급일 전체를 파이썬으로 시뮬레이션해보니, 시작점 제외로
바꾼 뒤에도 **30개 창에서 `foundCount=5`**가 나왔다(예:
`windowEnd=2005-06-13`, `windowStart(제외)=2004-06-13`인 창에
2004-06-14/2004-09-13/2004-11-29/2005-03-11/2005-06-13 5건).

**진짜 원인**: 실제 분기 배당 지급 간격은 정확히 91.25일이 아니다 —
KO 실데이터 기준 77일~102일까지 들쭉날쭉하다. 그래서 창 경계를 아무리
정확히 정의해도(시작점 제외든 포함이든), **어떤 12개월 롤링 창은
지급 간격이 우연히 짧아서 자연스럽게 5번을 담는다.** 이건 경계
정의의 문제가 아니라 실제 배당 캘린더가 정확히 주기적이지 않다는
사실 자체의 결과다.

이게 왜 문제냐면: `foundCount`가 4↔5로 자연스럽게 오갈 때, TTM
`actualSum`(raw 합계)을 그대로 비교하면 회사가 배당을 안 바꿨는데도
"이번엔 5번 받아서 합계가 늘었다 → 다음엔 4번이라 줄었다"는 식으로
가짜 삭감/가짜 성장 신호가 생긴다.

### 최종 결정

1. `TtmDividendSummary`의 `foundCount > expectedCount` 금지 불변식을
   제거한다 — 초과는 에러가 아니라 정상이다. `isComplete()`도
   `foundCount == expectedCount`에서 `foundCount >= expectedCount`로
   바꿔서 "부족(구멍)만 불완전, 초과는 완전"으로 재정의한다.
2. 삭감 판정 비교 기준을 `actualSum`에서 **`annualizedSum`**
   (`= actualSum * N / foundCount`)으로 바꾼다 — 지급 횟수 차이를
   자동으로 정규화해서 캘린더 드리프트로 인한 가짜 신호를 없앤다.
   `foundCount == expectedCount`인 일반적인 경우엔 `annualizedSum ==
   actualSum`이라 기존 손계산 케이스와 결과가 달라지지 않는다.

### 바뀐 것 (추가)

- `TtmDividendSummary` — 생성자 불변식에서 상한 제거, `isComplete()`
  재정의.
- `DividendCutDetector` — `actualSum` 대신 `annualizedSum`으로 비교,
  `CutComparisonResult`의 `previousTtmAmount`/`currentTtmAmount`도
  `annualizedSum` 기준으로 채움.
- `docs/specs/dividend-cut-detection.md` 1.2~1.4절.

### 교훈

시작점 제외/포함 같은 "경계 정의"만 고치면 될 거라고 판단했는데,
실제 22년치 KO 데이터로 전수 시뮬레이션을 돌려보고서야 더 근본적인
원인(불규칙한 실제 지급 간격)을 발견했다. **작은 손계산 테스트
케이스만으로는 이 문제가 절대 안 드러난다** — 실제 데이터 전체를
훑어야 나온다. `/verify`가 손계산 케이스 대조에 그치지 않고, 가능하면
실제 데이터 전체를 스캔해보는 습관이 왜 필요한지 보여주는 사례.
