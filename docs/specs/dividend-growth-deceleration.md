# 배당 성장률 둔화 (Dividend Growth Deceleration)

최근 배당 성장 속도가 장기 평균보다 느려지고 있는지를 CAGR(연복리성장률)
두 개를 비교해서 본다.

## 0. 스코프

- 대상은 [[dividend-cut-detection]]과 동일하게 **정기 배당(`DividendType.REGULAR`)만**.
  이미 만들어둔 `TtmDividendAggregationService`(분할 조정 + 캘린더
  드리프트 정규화 완료, [[05-ttm-window-boundary-fix]])를 그대로 재사용한다.
- 이 지표는 "성장이 둔화되고 있는가/얼마나"만 답한다. "그래서 매도해야
  한다"류의 투자 판단 문구는 만들지 않는다(CLAUDE.md 절대 규칙).
- **PROJECT.md가 "CAGR"을 명시했으므로 진짜 CAGR(n제곱근)로 구현한다** —
  더 단순한 대체 지표(연도별 성장률의 산술평균)로 타협하지 않기로
  사용자 확인 완료. `BigDecimal`은 정수 거듭제곱(`pow(int)`)만 지원해서
  분수 지수(1/n) 연산이 없으므로, **뉴턴-랩슨법으로 n제곱근을 직접
  구현**한다 — `Math.pow`/`double`은 절대 쓰지 않는다(CLAUDE.md,
  ArchUnit이 강제).

## 1. 계산식

### 1.1 입력 변수

| 변수 | 정의 |
|---|---|
| `t1` | 기준 시점 (조회 요청 시점의 `asOf`, 사용자 지정) |
| `N` | 해당 종목의 연간 정기 배당 지급 횟수 (`Ticker.regularPaymentsPerYear`, 필수 입력) |
| `D(t)` | `t`를 창 끝으로 하는 TTM 정규화 배당 합계 — `TtmDividendAggregationService.summarize(ticker, t, N).annualizedSum()`. `actualSum`이 아니라 `annualizedSum`을 쓰는 이유는 1.2절 |

### 1.2 왜 `actualSum`이 아니라 `annualizedSum`인가

`docs/decisions/05-ttm-window-boundary-fix.md`의 "추가 발견"에서 확인한
그대로다 — 실제 배당 캘린더는 91.25일 간격이 아니라 불규칙해서, TTM
창마다 지급 횟수가 자연스럽게 오갈 수 있다. `actualSum`을 그대로
CAGR에 넣으면 회사가 배당을 안 바꿨는데도 캘린더 드리프트 때문에
가짜 성장/가짜 둔화가 나온다. `annualizedSum`(지급 횟수 차이를
정규화한 값)으로 비교하면 이 문제가 자동으로 없어진다 — 삭감 이력
탐지에서 이미 검증한 것과 같은 원칙을 여기도 그대로 적용한다.

### 1.3 CAGR 계산 — n제곱근 (뉴턴-랩슨법)

```
CAGR(n) = nthRoot(D(t1) / D(t1 - n년), n) - 1
```

`nthRoot(x, n)`은 `x^(1/n)`을 뉴턴-랩슨 반복으로 구한다:

```
r_0 = 1  (초기값 — 배당 비율은 보통 0.1~10배 범위라 1부터 시작해도
          반복 횟수 안에서 충분히 수렴함)
r_{k+1} = ((n-1) * r_k + x / r_k^(n-1)) / n

수렴 조건: |r_{k+1} - r_k| < 1E-15 이거나 반복 횟수 100회 도달
전부 MathContext.DECIMAL64로 계산 (+,-,*,/만 사용 — Math.pow/double 금지)
```

`x = 0`이면 `nthRoot(0, n) = 0`으로 바로 반환(반복 없이). `x < 0`은
입력으로 들어오지 않는다 — 배당 합계는 항상 0 이상이라 비율도 항상
0 이상이다.

### 1.4 단기/장기 CAGR과 둔화 판정

```
CAGR_short = CAGR(3)   // 최근 3년
CAGR_long  = CAGR(10)  // 최근 10년

CAGR_short < CAGR_long  →  성장 둔화
CAGR_short >= CAGR_long →  둔화 아님

decelerationGap = CAGR_long - CAGR_short   // 둔화일 때만 부가 정보로 노출, 아니면 null
```

기간을 3년/10년으로 고정한 이유: PROJECT.md 5.2절이 예시로 명시한
값을 그대로 채택(사용자 확인 완료).

### 1.5 BigDecimal 연산 규칙

- `D(t)` 자체는 `TtmDividendAggregationService`를 그대로 호출한 결과라
  이미 `MathContext.DECIMAL64`.
- `nthRoot` 반복 계산도 전부 `MathContext.DECIMAL64`.
- 둔화 판정(`CAGR_short < CAGR_long`)은 `BigDecimal.compareTo` — 반올림
  없이 정확한 비교.
- 반올림은 최종 출력(%p 변환) 직전에만 적용한다([[yield-change-decomposition]],
  [[dividend-cut-detection]]과 동일한 원칙).

## 2. 기준 시점

- `t1`은 조회 요청 시점의 `asOf`(사용자 지정 날짜) — [[yield-change-decomposition]]의
  `t1`과 동일한 개념(가격이 아니라 배당 TTM 창의 기준일로 씀).
- `t1 - 3년`, `t1 - 10년`은 `t1.minusYears(3)`, `t1.minusYears(10)`.
- TTM 창(`D(t)` 내부)의 기준은 ex-dividend date — 이 프로젝트 전체에서
  이미 쓰고 있는 기준([[yield-change-decomposition]], [[dividend-cut-detection]]과 동일).

## 3. 반올림 방향

- 중간 계산(`nthRoot`, `CAGR` 자체)은 반올림하지 않는다(1.5 참고).
- 화면/API에 노출하는 최종 `CAGR_short`, `CAGR_long`, `decelerationGap`은
  **%p 단위로 변환(×100) 후 소수 2자리, `RoundingMode.HALF_UP`** —
  기존 두 지표와 동일한 이유(화면에 보여주는 일반적인 퍼센트 지표 관례).

## 4. 예외 케이스

| 상황 | 처리 |
|---|---|
| `N`(`regularPaymentsPerYear`)이 설정 안 됨 | 계산 자체 불가 → `IllegalStateException` (기존 두 지표와 동일한 예외 패턴 재사용) |
| 알 수 없는 티커 | `NoSuchElementException` |
| `D(t1)`, `D(t1-3)`, `D(t1-10)` 중 하나라도 TTM 창이 불완전(`foundCount < N`, 데이터 구멍) | 그 CAGR은 계산하지 않고 "계산 불가(데이터 불완전)"로 표시 — 조용히 넘기지 않는다(CLAUDE.md). `foundCount > N`(캘린더 드리프트로 인한 여분)은 완전으로 취급(1.2절) |
| `D(t1-3)` 또는 `D(t1-10)`가 0(그 시점 이전엔 배당 지급 이력이 아예 없음, 예: 상장 10년 미만) | 해당 CAGR은 0으로 나누기라 계산 불가 — "이 구간 배당 이력 없음"으로 표시, 절대 추정치로 대체하지 않는다 |
| 창 구간에 분할이 있었음 | `TtmDividendAggregationService`가 이미 분할 조정하므로 추가 처리 불필요 — 기존 로직 재사용 |
| 특별배당 혼입 | `DividendType.REGULAR`만 조회하므로 자동 배제 (0절) |
| 캘린더 드리프트로 TTM 지급 횟수가 N보다 많음 | 완전으로 취급, `annualizedSum`으로 이미 정규화됨(1.2절) |
| 단기/장기 CAGR 중 하나만 계산 가능한 경우(예: 상장 5년 차라 10년 CAGR은 불가하지만 3년은 가능) | 계산 가능한 쪽만 노출하고 나머지는 "계산 불가" — 둘 다 있어야만 둔화 판정을 시도(한쪽이 없으면 비교 자체가 불가하므로 둔화 판정도 "판정 불가") |

## 5. CLAUDE.md 정합성 체크

- ✅ 전 과정 `BigDecimal`, `double`/`float` 미사용 — `nthRoot`도 Newton-Raphson을
  BigDecimal 연산만으로 구현(ArchUnit이 강제)
- ✅ 특별배당은 정기 배당과 분리해서 다룸(0절)
- ✅ 데이터 불완전 시 조용히 넘어가지 않고 "계산 불가"로 표시(4절)
- ✅ 투자 판단/추천 문구 없음 — "둔화 여부/폭"만 보여주고 "그래서 매도해라"는 말하지 않음
- ✅ 서비스 계층은 DB만 읽음 — 외부 API 호출 없음(이미 수집된 `DividendPayment`/`SplitEvent`만 사용)
