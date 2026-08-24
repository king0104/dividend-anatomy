# 12. 배당 안전도 스코어 데이터 제공자 선정

**날짜**: 2026-08-25

## 배경

"배당연습장" 확장 기획(6~7장, 배당 안전도 0~100점)은 배당성향·FCF 대비
배당 비율·ROE·부채비율·이자보상배율 5개 재무 지표를 필요로 한다. 이 중
배당성향 하나만 필요했던 `09-payout-ratio-descoped.md` 때와 달리, 이번엔
5개 전부라 데이터 제공자 실현 가능성을 먼저 확인해야 했다. Massive·Twelve
Data는 이미 배당·분할·주가 전용으로 검증돼 있고(`01-data-source.md`)
재무제표 데이터는 주지 않는다.

## 검증 방법

Massive/Twelve Data 검증 때와 같은 방식 — 실제 API를 호출해 확인한다.

### Twelve Data `/statistics` 등 — 데모 키로는 통과, 실키로는 전부 막힘

공개 데모 키로 `/statistics?symbol=AAPL`을 호출하니 배당성향(`payout_ratio`),
ROE(`return_on_equity_ttm`), 부채비율(`total_debt_to_equity_mrq`),
FCF(`levered_free_cash_flow_ttm`)가 필드로 직접 왔다. 그런데 이 프로젝트가
실제로 쓰고 있는 무료 플랜 키(`k8s/secret.yaml`의 `TWELVEDATA_TOKEN`)로
같은 요청을 재현하니:

| 엔드포인트 | 결과 |
|---|---|
| `/statistics` | HTTP 403 — "Pro/Ultra/Venture/Enterprise 플랜 전용" |
| `/income_statement` | HTTP 403 — 동일 사유 |
| `/balance_sheet` | HTTP 403 — 동일 사유 |
| `/cash_flow` | HTTP 403 — 동일 사유 |

데모 키는 가입 유도용으로 프리미엄 응답을 그대로 보여주는 함정이었다.
**Twelve Data는 이 용도로 배제.**

### Alpha Vantage — IBM 데모 키 → KO/AAPL 실키 순서로 확인

먼저 공개 데모 키(`apikey=demo`, IBM 한정)로 `OVERVIEW`, `BALANCE_SHEET`,
`CASH_FLOW`, `INCOME_STATEMENT` 4개 엔드포인트를 확인했다. 필요한 원재료가
전부 있었다:

| 지표 | 계산식 | IBM 값(데모 키) |
|---|---|---|
| ROE | `ReturnOnEquityTTM`(직접 제공) | 34.5% |
| 배당성향 | `DividendPerShare`÷`EPS` | 59.7% |
| FCF 대비 배당 | `dividendPayout`÷(`operatingCashflow`−`capitalExpenditures`) | 51.7% |
| 부채비율 | `totalLiabilities`÷`totalShareholderEquity` | 3.65배 |
| 이자보상배율 | `ebit`÷`interestExpense` | 6.34배 |

Twelve Data에서 데모 키와 실키 결과가 달랐던 전례가 있어, 실제 무료 키를
발급받아 브랜드 풀에 들어갈 실제 종목(KO, AAPL)으로 재확인했다. 4개
엔드포인트 전부 정상 응답했고, 5개 지표 모두 계산 가능함을 확인했다:

| 지표 | 계산식 | KO 값(실키) |
|---|---|---|
| ROE | `ReturnOnEquityTTM` | 42% |
| 배당성향 | `DividendPerShare`÷`EPS` | 62.5% |
| FCF 대비 배당 | `dividendPayout`÷(`operatingCashflow`−`capitalExpenditures`) | 165.7% |
| 부채비율 | `totalLiabilities`÷`totalShareholderEquity` | 219.3% |
| 이자보상배율 | `ebit`÷`interestExpense` | 10.67배 |

KO의 FCF 대비 배당 비율(165.7%)이 100%를 넘는 것은 오히려 좋은
신호다 — 실제 종목에서 "위험" 쪽 신호가 나온다는 뜻이라, 지표가 죽어있는
숫자가 아니라 실제로 변별력이 있음을 보여준다.

## 판단

- **채택**: Alpha Vantage 무료 플랜, `OVERVIEW`/`BALANCE_SHEET`/`CASH_FLOW`/
  `INCOME_STATEMENT` 4개 엔드포인트.
- **배제**: Twelve Data(재무제표 엔드포인트 전부 유료 전용으로 확인됨),
  Massive(재무제표 엔드포인트가 2026-06-22 sunset, 후속 Fundamentals API는
  유료 Add-on 전용), OpenDART(국내 종목 전용이라 미국 중심 브랜드 풀과
  무관 — 이 프로젝트는 국내 종목을 아예 다루지 않기로 결정함).

## 운영 제약

- **초당 1콜, 하루 25콜 제한**이 실제로 걸린다(연속 호출 시 "Please
  consider spreading out your free API requests" 응답으로 확인).
- 브랜드 풀 8개 × 4콜 = 32콜로 하루 한도를 초과한다. 배치를 이틀에 나누거나,
  `01-data-source.md`에서 이미 확인한 "verified open-source project" 무제한
  신청(공개 GitHub 저장소 기준 신청 가능)을 진행해야 한다.

## 남은 위험

- 실키 검증은 KO·AAPL 두 종목만 했다. 브랜드 풀 나머지 종목(MSFT, MCD, NKE
  등)에서 특정 필드가 null로 오는 경우가 있는지는 실제 배치 구현 시
  확인이 필요하다.
- Alpha Vantage 무료 플랜의 공개 배포 약관은 `01-data-source.md`에서
  "완전히 확정된 건 아니다"로 남겨둔 상태다 — Massive/Twelve Data와 같은
  회색지대 리스크를 그대로 안고 간다.
