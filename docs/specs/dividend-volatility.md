# 배당 변동성 (Dividend Volatility)

연도별 배당금 증감률이 얼마나 들쭉날쭉한지를 표준편차로 본다.
PROJECT.md 5.2절: "배당금 증감률의 표준편차", 표본 기간은 이 프로젝트가
직접 정한다.

## 0. 스코프

- 대상은 [[dividend-cut-detection]], [[dividend-growth-deceleration]]와
  동일하게 **정기 배당(`DividendType.REGULAR`)만**. `TtmDividendAggregationService`의
  `annualizedSum`(분할 조정 + 캘린더 드리프트 정규화 완료)을 그대로
  재사용한다.
- **표본 기간은 최근 10년**(사용자 확인 완료) — [[dividend-growth-deceleration]]의
  장기 CAGR 구간(10년)과 맞춰서 일관성을 유지한다.
- **표본 표준편차**(`N-1`로 나눔, 사용자 확인 완료) — 제한된 연도만
  관찰해서 전체 변동성을 추정하는 상황이라 통계적 관례(베셀 보정)상
  이쪽이 더 적절하다고 판단.
- 표준편차 계산엔 제곱근이 필요하다. [[dividend-growth-deceleration]]의
  `nthRoot`(n제곱근, 뉴턴-랩슨 직접 구현)과 달리, 제곱근은 Java 9부터
  `BigDecimal.sqrt(MathContext)`가 **표준 라이브러리에 이미 있다** —
  직접 구현하지 않고 이걸 그대로 쓴다(불필요한 재구현 금지, 검증된
  구현 재사용).
- 이 지표는 "변동성이 얼마인가"만 답한다. "그래서 위험한 종목이다"류의
  투자 판단 문구는 만들지 않는다(CLAUDE.md 절대 규칙).

## 1. 계산식

### 1.1 입력 변수

| 변수 | 정의 |
|---|---|
| `t1` | 기준 시점 (조회 요청 시점의 `asOf`, 사용자 지정) |
| `N` | 연간 정기 배당 지급 횟수 (`Ticker.regularPaymentsPerYear`, 필수 입력) |
| `D_i` | `i`년 전 시점(`t1 - i년`)을 창 끝으로 하는 TTM 정규화 배당 —`TtmDividendAggregationService.summarize(ticker, t1 - i년, N).annualizedSum()`, `i = 0..10` (11개 지점) |
| `g_i` | `i`번째 연간 증감률 = `(D_{i-1} - D_i) / D_i`, `i = 1..10` (10개 값 — `D_{i-1}`이 `D_i`보다 1년 더 최근) |

### 1.2 표준편차

```
mean = sum(g_1..g_10) / 10

variance = sum((g_i - mean)^2, i=1..10) / (10 - 1)     // 표본 분산 (N-1)

stddev = variance.sqrt(MathContext.DECIMAL64)            // BigDecimal 표준 라이브러리
```

### 1.3 BigDecimal 연산 규칙

- `D_i`는 이미 `MathContext.DECIMAL64`로 계산된 값(`TtmDividendAggregationService`
  재사용).
- `g_i`, `mean`, `variance` 전부 `MathContext.DECIMAL64`.
- `stddev`만 `BigDecimal.sqrt(MathContext.DECIMAL64)` 사용 — 나머지
  연산과 동일한 정밀도.
- 반올림은 최종 출력(%p 변환) 직전에만 적용한다(기존 두 지표와 동일 원칙).

## 2. 기준 시점

- `t1`은 조회 요청 시점의 `asOf`(사용자 지정 날짜) — [[dividend-growth-deceleration]]의
  `t1`과 동일한 개념.
- `D_i`의 창 끝은 `t1.minusYears(i)` (`i=0..10`).
- TTM 창(`D_i` 내부) 기준은 ex-dividend date — 이 프로젝트 전체에서
  이미 쓰고 있는 기준.

## 3. 반올림 방향

- 중간 계산(`g_i`, `mean`, `variance`, `stddev` 자체)은 반올림하지
  않는다(1.3 참고).
- 화면/API에 노출하는 최종 `stddev`(그리고 참고용 `mean`)는 **%p 단위로
  변환(×100) 후 소수 2자리, `RoundingMode.HALF_UP`** — 기존 두 지표와
  동일한 이유.

## 4. 예외 케이스

| 상황 | 처리 |
|---|---|
| `N`(`regularPaymentsPerYear`)이 설정 안 됨 | 계산 자체 불가 → `IllegalStateException` (기존 지표들과 동일한 예외 패턴) |
| 알 수 없는 티커 | `NoSuchElementException` |
| `D_0`~`D_10`(11개 지점) 중 하나라도 TTM 창이 불완전(`foundCount < N`, 데이터 구멍) | 전체 계산 불가 — "계산 불가(데이터 불완전)"로 표시. 11개 지점 전부 완전해야만 10개 증감률을 신뢰할 수 있음 |
| 상장 10년 미만이라 `D_10`이 아예 없음 | "10년치 이력 없음"으로 계산 불가 표시(추정치로 대체하지 않음) — [[dividend-growth-deceleration]]의 `INSUFFICIENT_DATA`와 동일한 취지 |
| 캘린더 드리프트로 TTM 지급 횟수가 N보다 많음(`foundCount > N`) | 완전으로 취급 — `annualizedSum`으로 이미 정규화됨(0절, [[05-ttm-window-boundary-fix]]) |
| 창 구간에 분할이 있었음 | `TtmDividendAggregationService`가 이미 분할 조정하므로 추가 처리 불필요 |
| 특별배당 혼입 | `DividendType.REGULAR`만 조회하므로 자동 배제 (0절) |
| `D_i == 0`(그 시점 이전엔 배당 지급 이력이 없음) | `g_i` 계산이 0으로 나누기라 불가 — 전체 계산을 "이 구간 배당 이력 없음"으로 표시 |

## 5. CLAUDE.md 정합성 체크

- ✅ 전 과정 `BigDecimal`, `double`/`float` 미사용 — 제곱근도
  `BigDecimal.sqrt(MathContext)`(표준 라이브러리, ArchUnit이 강제하는
  domain 패키지 규칙과 충돌 없음)
- ✅ 특별배당은 정기 배당과 분리해서 다룸(0절)
- ✅ 데이터 불완전 시 조용히 넘어가지 않고 "계산 불가"로 표시(4절)
- ✅ 투자 판단/추천 문구 없음 — "변동성이 얼마다"만 보여주고 "그래서
  위험하다"는 말하지 않음
- ✅ 서비스 계층은 DB만 읽음 — 외부 API 호출 없음
