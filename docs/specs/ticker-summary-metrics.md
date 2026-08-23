# 종목 요약 지표 (Ticker Summary Metrics)

PROJECT.md 4.1절 종목 목록 화면에 필요한 두 지표를 하나로 묶는다 —
둘 다 목록 화면 한 행에 같이 나가고, 둘 다 기존 TTM/가격 조회
인프라를 재사용하는 작은 계산이라 별도 스펙 두 개로 쪼개는 것보다
한 문서에서 같이 정의하는 게 낫다고 판단했다(사용자 확인 없이 결정 —
근거는 이 문단).

1. **현재 시가배당률** — 최신 TTM 연환산 배당 ÷ 현재가
2. **연속 배당 증가 연수** — 실제 "배당킹" 정의(역년 단위 총배당
   비교)를 재현. 기존 [[dividend-cut-detection]]의 지급일마다 롤링
   TTM 비교와는 **다른 개념** — 이건 "연간 총액"을 역년으로 비교한다.

## 0. 스코프

- 둘 다 **정기 배당(`REGULAR`)만** 대상. 특별배당은 제외
  ([[special-dividend-disclosure]] 0절과 동일 원칙).
- 둘 다 **분할 조정된 금액**으로 계산한다. 현재
  `TtmDividendAggregationService.splitAdjustedAmount()`가 `private`인데,
  연속 증가 연수 계산도 같은 조정이 필요하므로 이 메서드를
  `domain.split.SplitAdjustmentCalculator`(신규, 순수)로 추출해서 두
  곳에서 공유한다 — 분할 조정 로직은 이미 한 번 버그가 났던 지점
  ([[03-split-adjustment]], [[04-ttm-window-boundary-overlap]])이라
  복제하지 않고 재사용하는 쪽을 택한다.
- **연속 증가 연수는 진행 중인 올해(currentYear)를 계산에서 제외한다.**
  올해는 아직 남은 지급이 안 끝났을 수 있어서, 부분 합계를 완결된
  작년 합계와 비교하면 "감소"로 오판할 수 있다(실제 배당킹 지수도
  연말 확정 전까지는 그 해를 스트릭에 반영하지 않는다). 가장 최근
  **완료된**(작년 이전) 역년부터 거꾸로 비교한다.

## 1. 계산식

### 1.1 현재 시가배당률

| 변수 | 정의 |
|---|---|
| `asOf` | `LocalDate.now()` |
| `TTM(asOf)` | `TtmDividendAggregationService.summarize(ticker, asOf, N)` — 기존 그대로 |
| `P(asOf)` | `asOf` 기준 "가장 가까운 값" 종가 — `PriceBarRepository.findTopByTickerAndDateLessThanEqualOrderByDateDesc` |

```
currentYield = TTM(asOf).annualizedSum / P(asOf)
dataComplete = TTM(asOf).isComplete()
```

`annualizedSum`을 쓰는 이유는 [[dividend-cut-detection]] 1.3절과
동일(캘린더 드리프트 정규화).

### 1.2 연속 배당 증가 연수

| 변수 | 정의 |
|---|---|
| `N` | `Ticker.regularPaymentsPerYear` |
| `currentYear` | `LocalDate.now().getYear()` |
| `payments` | 해당 종목의 `DividendType.REGULAR` 전체 지급 이력 |
| `years` | `payments`의 ex-dividend date 연도 중 `currentYear` 미만인 값들의 유니크 오름차순 목록 |
| `annualTotal(y)` | `sum(SplitAdjustmentCalculator.adjustedAmount(ticker, p) for p in payments where year(p.exDividendDate) == y)` |
| `paymentCount(y)` | 위 합산에 쓰인 지급 건수 |

```
streak 계산 (가장 최근 연도부터 거꾸로):

y = max(years)
streak = 0
loop:
  if y not in years or paymentCount(y) < N:
      → y 연도가 불완전(데이터 구멍) → 여기서 중단, 지금까지의 streak 확정
  prevYear = y - 1
  if prevYear not in years or paymentCount(prevYear) < N:
      → 비교할 이전 연도가 없거나 불완전 → 여기서 중단, 지금까지의 streak 확정
  if annualTotal(y) > annualTotal(prevYear):
      streak += 1; y = prevYear; loop 계속
  else:
      → 증가가 끊김(감소 또는 동일) → 여기서 중단, 지금까지의 streak 확정
```

- `years`가 2개 미만이면(비교 자체가 불가능) `streak` 계산을 시도하지
  않고 `INSUFFICIENT_DATA` 상태를 반환한다.
- **"동일(동결)"은 증가가 아니다** — 실제 배당킹 정의가 "매년 인상"을
  요구하므로, 전년과 같은 금액이면 스트릭이 끊긴다(사용자 결정 없이
  PROJECT.md 원문 "연속 배당 **증가**"를 그대로 따름 — "유지"가
  아니라 "증가").

## 2. 기준 시점

- 현재 시가배당률: `asOf = LocalDate.now()`, 가격은 "가장 가까운 값"
  원칙(프로젝트 전체 관례) 그대로.
- 연속 증가 연수: 역년(calendar year) 단위, ex-dividend date 기준으로
  연도를 분류한다(프로젝트 전체가 이미 ex-dividend date를 기준
  날짜로 쓰고 있음 — [[yield-change-decomposition]],
  `TtmDividendAggregationService` 전부 동일).

## 3. 반올림 방향

- `currentYieldPercent`: 다른 %p 지표들과 동일하게 **소수 2자리,
  `RoundingMode.HALF_UP`**, ×100 후 반올림(응답 매핑 시점에만 적용,
  중간 계산은 `MathContext.DECIMAL64`).
- `연속 증가 연수`는 정수(횟수)이므로 반올림 대상이 아니다.
- 연도별 합계 비교(`annualTotal(y) > annualTotal(y-1)`)는 반올림 없이
  `BigDecimal.compareTo`로 정확 비교([[dividend-cut-detection]] 1.5절과
  동일 원칙).

## 4. 예외 케이스

| 상황 | 처리 |
|---|---|
| `regularPaymentsPerYear` 없음 | 두 지표 모두 계산 불가 → `IllegalStateException`(기존 패턴 재사용) |
| 가격 데이터가 전혀 없음 | 현재 시가배당률만 `null` + 사유("가격 데이터 없음") — 연속 증가 연수는 가격과 무관하므로 영향 없음 |
| TTM 창이 불완전(`foundCount < N`) | 시가배당률은 계산하되 `dataComplete=false`로 표시(조용히 넘어가지 않음, [[time-series-integrity-logging]] WARN 로그도 이미 같은 지점에서 남음) |
| 완료된 과거 연도가 2개 미만 | 연속 증가 연수 = `INSUFFICIENT_DATA`(계산 불가, 0이 아님 — "0년 연속"과 "판정 불가"는 다른 의미) |
| 특정 과거 연도의 지급 건수가 `N` 미만(데이터 구멍) | 그 연도를 경계로 스트릭 계산을 중단하고 확정 — 구멍 너머 연도는 신뢰하지 않고 포함하지 않는다(조용히 이어붙이지 않음) |
| 전년 대비 동일 금액(인상도 삭감도 아님) | 증가로 치지 않음 — 스트릭 종료(0절) |
| 분할이 있었던 연도 | `SplitAdjustmentCalculator`로 조정 후 합산 — 새 리스크 아님, 기존 로직 재사용 |
| 특별배당 혼입 | `REGULAR`만 집계(0절) |
| 종목이 배당을 아예 지급한 적 없음 | 연속 증가 연수 `INSUFFICIENT_DATA`, 시가배당률도 `TTM.foundCount=0` → `annualizedSum=null` → 시가배당률 `null` |

## 5. CLAUDE.md 정합성 체크

- ✅ `BigDecimal`만 사용(ArchUnit 강제)
- ✅ 특별배당은 정기 배당과 분리(0절)
- ✅ 데이터 불완전 시 조용히 넘어가지 않음 — `dataComplete` 플래그,
  스트릭 계산 중단 경계를 명시적으로 노출
- ✅ 서비스 계층은 DB만 읽음
- ✅ 투자 판단/추천 문구 없음 — "몇 년 연속 증가했는지"만 보여주고
  "그래서 좋다"는 말하지 않음
- ⚠️ **분할 조정 로직을 `private` 메서드에서 공유 유틸리티로 추출하는
  리팩터를 포함한다** — 기존 `TtmDividendAggregationService`의 동작은
  바꾸지 않고(위임만 하도록) 시그니처도 그대로 유지해서 회귀 위험을
  최소화한다. 기존 테스트가 그대로 통과하는지로 확인.
