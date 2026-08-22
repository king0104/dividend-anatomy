# 삭감 이력 탐지 (Dividend Cut Detection)

정기 배당 지급 이력에서 "실질적으로 배당이 줄어든" 구간을 찾아낸다.
raw 지급액만 비교하면 분할·지급주기 변경을 삭감으로 오탐하므로, 이미
만들어둔 [[yield-change-decomposition]]의 TTM 집계(분할 조정 포함)를
그대로 재사용한다.

## 0. 스코프

- 대상은 **정기 배당(`DividendType.REGULAR`)만**. 특별배당은 이미
  분류돼서 들어온다고 전제 — 이 스펙은 정기/특별 분류 로직을 다시
  하지 않는다 ([[yield-change-decomposition]] 0절과 동일한 전제).
- "삭감"의 판정 단위는 **TTM(직전 12개월) 합계**다. 개별 지급 1건씩
  raw 금액으로 비교하지 않는다 — 이유는 1절 참고.
- 이 지표는 "삭감이 있었는가/언제인가"만 답한다. "그래서 위험한
  종목이다"류의 투자 판단 문구는 만들지 않는다(CLAUDE.md 절대 규칙).

## 1. 계산식

### 1.1 왜 raw 금액 직접 비교가 아니라 TTM 비교인가

Massive 배당 금액은 raw(분할 미조정)다
([[03-split-adjustment]]). 2:1 분할이 있으면 다음 지급액이 정확히
반토막 나는데, 이걸 raw로 비교하면 실제로는 삭감이 아닌데 삭감으로
오탐한다. 또한 지급 주기가 바뀌는 경우(예: 연 1회 → 분기 4회)도 건별
raw 금액 비교로는 오탐한다. TTM 합계(이미 분할 조정됨, [[yield-change-decomposition]]
1.2절의 `TtmDividendAggregationService`가 하는 정확히 그 계산)로 비교하면
두 문제 다 자연스럽게 해결된다.

### 1.2 입력 변수

| 변수 | 정의 |
|---|---|
| `payments` | 해당 종목의 `DividendType.REGULAR` 지급 이력 전체, ex-dividend date 오름차순 정렬 |
| `N` | 해당 종목의 연간 정기 배당 지급 횟수 (`Ticker.regularPaymentsPerYear`, 필수 입력) |
| `TTM(t)` | ex-dividend date `t`를 창 끝으로 하는 TTM 집계 — `TtmDividendAggregationService.summarize(ticker, t, N)`의 결과 그대로 (`actualSum`, `annualizedSum`, `foundCount`) |

**창 정의는 `(t-12개월, t]`(시작점 제외, 끝만 포함)이다** —
`[t-12개월, t]`(양 끝 포함)로 하면 정확히 12개월 차이 나는 두 지급일이
인접한 두 창에 모두 걸려 이중 계산된다. 실제 KO 데이터로 이 문제를
확인하고 고친 과정은 `docs/decisions/05-ttm-window-boundary-fix.md` 참고.

### 1.3 삭감 판정 알고리즘

```
payments를 ex-dividend date 오름차순으로 순회하면서, 두 번째 지급(i=1)부터:

  prev = TTM(payments[i-1].exDividendDate)
  curr = TTM(payments[i].exDividendDate)

  두 TTM 창 중 하나라도 foundCount < N (불완전, 데이터 구멍)이면:
      → "판정 불가" (삭감도 정상도 아님, 데이터 불완전으로 별도 표시)
  아니면 (두 창 모두 foundCount >= N, 완전 — foundCount가 N을 넘는 것도 완전):
      curr.annualizedSum < prev.annualizedSum  →  삭감 이벤트, 시점 = payments[i].exDividendDate
      curr.annualizedSum >= prev.annualizedSum →  정상 (삭감 아님)

payments가 0건 또는 1건이면 비교 대상 자체가 없음 → 삭감 이벤트 없음
(에러 아님, 그냥 빈 결과).
```

**왜 `actualSum`이 아니라 `annualizedSum`으로 비교하는가**: 실제 배당
캘린더는 정확히 91.25일(365.25일/4) 간격이 아니다 — KO 실데이터
기준으로 지급 간격이 77일~102일까지 들쭉날쭉하다. 그래서 창 정의를
아무리 정확히 해도, 롤링 12개월 창 안에 분기 배당이 **자연스럽게
5번 들어가는 해**가 있다(캘린더 드리프트, 버그 아님). `actualSum`을
그대로 비교하면 `foundCount`가 4↔5로 바뀔 때마다 회사가 배당을
바꾸지 않았는데도 가짜 삭감/가짜 성장 신호가 생긴다. `annualizedSum`
(`= actualSum * N / foundCount`)은 이 지급 횟수 차이를 자동으로
정규화하므로 비교 기준으로 쓴다. `foundCount == N`인 경우
`annualizedSum == actualSum`이라 기존 손계산 케이스와 결과가 달라지지
않는다.

`TtmDividendSummary`의 `isComplete()`는 `foundCount >= expectedCount`로
정의된다 — 부족(구멍)만 불완전이고, 초과(캘린더 드리프트)는 완전으로
취급한다. `foundCount==0`이면 `annualizedSum`은 `null`이라(불변식),
이력이 막 시작된 시점의 "직전 TTM이 0건"인 경우는 애초에 `isComplete()`가
`false`라 "불완전 → 판정 불가"로 걸러진다.

### 1.4 감소율(%) 계산 — 삭감 이벤트에만 부가 정보로 노출

```
decreasePercent = (prev.annualizedSum - curr.annualizedSum) / prev.annualizedSum * 100
```

`prev.annualizedSum`이 0이 되는 경우는 1.3의 "불완전 → 판정 불가" 규칙에서
이미 걸러지므로(완전한 TTM 창인데 합계가 0이려면 N=0이어야 하는데
`TtmDividendSummary` 생성자가 `expectedCount > 0`을 강제) 실제로
발생하지 않는다.

### 1.5 BigDecimal 연산 규칙

- `TTM(t)` 자체는 `TtmDividendAggregationService`를 그대로 호출한
  결과이므로 이미 `MathContext.DECIMAL64`로 계산돼 있다.
- 삭감 여부 판정(`curr < prev`)은 `BigDecimal.compareTo` — 반올림
  없이 정확한 비교.
- `decreasePercent` 계산에만 `MathContext.DECIMAL64`를 사용하고,
  반올림은 최종 출력 직전에만 적용한다([[yield-change-decomposition]]
  1.5절과 동일한 원칙).

## 2. 기준 시점

- 정기 배당 지급 이력의 **ex-dividend date** 기준으로 정렬·비교한다
  (이 프로젝트 전체에서 이미 쓰고 있는 기준 — [[yield-change-decomposition]],
  `TtmDividendAggregationService` 전부 ex-dividend date 기준).
- 삭감 이벤트의 "발생 시점"은 감소가 처음 관측된 지급의 ex-dividend
  date로 기록한다(지급액 자체가 줄어든 payDate가 아니라, TTM이
  줄어들기 시작한 그 지급의 ex-date).

## 3. 반올림 방향

- 삭감 여부 판정 자체(`curr < prev`)는 반올림하지 않는다 — BigDecimal
  정확 비교.
- 부가 정보로 노출하는 `decreasePercent`만 **소수 2자리,
  `RoundingMode.HALF_UP`** — [[yield-change-decomposition]] 3절과
  동일한 이유(화면에 보여주는 일반적인 퍼센트 지표 관례).

## 4. 예외 케이스

| 상황 | 처리 |
|---|---|
| 정기 배당 이력이 0건 또는 1건 | 비교 대상 없음 → 삭감 이벤트 없는 빈 결과 반환 (에러 아님) |
| `N`(`regularPaymentsPerYear`)이 설정 안 됨 | 계산 자체 불가 → `IllegalStateException` ([[yield-change-decomposition]]의 `YieldDecompositionService`와 동일한 예외 패턴 재사용) |
| 두 지급 사이 TTM 창 중 하나라도 불완전(`foundCount < N`, 구멍) | 삭감/정상으로 확정 판정하지 않고 **"판정 불가(데이터 불완전)"**로 별도 분류해서 노출 — 조용히 넘기지 않는다(CLAUDE.md) |
| 캘린더 드리프트로 창에 지급이 N번보다 많이 들어감(`foundCount > N`) | 불완전이 아니라 **완전**으로 취급(`isComplete()`), `annualizedSum`으로 정규화해서 비교 — 자세한 근거는 1.3절, `docs/decisions/05-ttm-window-boundary-fix.md` |
| 창 구간에 분할이 있었음 | `TtmDividendAggregationService`가 이미 분할 조정하므로 이 계산식에서 추가 처리 불필요 — 기존 로직 재사용, 새 리스크 아님 |
| 특별배당 혼입 | `DividendType.REGULAR`만 조회하므로 자동 배제 (0절) |
| 감소 임계값 | **0원보다만 줄어도 삭감으로 판정**한다(사용자 결정). 반올림·오차 허용 폭을 따로 두지 않는다 — BigDecimal 정확 비교라 애매한 경계값 자체가 없다 |
| 지급 주기(`N`)가 이력 중간에 바뀐 경우(예: 연배당 → 분기배당 전환) | TTM 비교 방식 자체가 이 문제를 흡수한다(1.1절) — 다만 전환 시점 전후로 `N` 값 자체가 바뀌어야 한다면 그건 `Ticker.regularPaymentsPerYear` 갱신 로직(`MassiveDividendIngestionService`)의 책임이지 이 스펙의 책임이 아니다 |

## 5. CLAUDE.md 정합성 체크

- ✅ 전 과정 `BigDecimal`, `double`/`float` 미사용 (ArchUnit이 강제)
- ✅ 특별배당은 정기 배당과 분리해서 다룸(0절)
- ✅ 데이터 불완전 시 조용히 넘어가지 않고 "판정 불가"로 표시(4절)
- ✅ 투자 판단/추천 문구 없음 — "삭감이 있었다/시점"만 보여주고
  "그래서 위험하다"는 말하지 않는다
- ✅ 서비스 계층은 DB만 읽음 — 외부 API 호출 없음(이미 수집된
  `DividendPayment`/`SplitEvent`만 사용)
