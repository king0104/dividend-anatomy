# 14. 배당 안전도 스코어 계산 공식

**날짜**: 2026-08-25

## 배경

`docs/decisions/12`에서 데이터 소스(Alpha Vantage)는 확정했지만, 5개 지표
(배당성향·FCF 대비 배당 비율·ROE·부채비율·이자보상배율)를 0~100점 하나로
합산하는 공식은 없었다. 기획서 1-7절도 "안전 30~60% / 위험 80~100%↑" 같은
정성적 구간만 줄 뿐 합산 방식은 없다.

내가 처음엔 이 구간을 적당히 선형보간하는 공식을 임의로 만들어 계획에
넣었는데, 사용자가 "임의로 정하지 말고 실제로 자주 쓰이는 지표·임계값을
웹에서 찾아서 근거 있게 만들라"고 요청했다. 실제로 조사해보니 **원래
계획의 부채비율 계산식 자체가 잘못돼 있었다** — 아래에 그 경위를 남긴다.

## 조사 결과 (출처)

- **[Simply Safe Dividends — Dividend Safety Scores](https://www.simplysafedividends.com/dividend-safety-scores/)**:
  0~100점 체계를 실제로 공개 중인 상용 서비스. 점수 밴드를 명시적으로
  공개함(0-20 매우위험/21-40 위험/41-60 경계/61-80 안전/81-100 매우안전).
  "60점 이하 종목이 코로나 시기 실제 배당 삭감 기업의 93%를 차지했다"고
  주장 — 60/61 경계가 실제 결과와 연동된 숫자라는 근거.
- **Marc Lichtenfeld, *Get Rich with Dividends***: 배당성향은 "75% 이하를
  보고, 100% 초과는 피하라"(REIT/유틸리티/MLP는 예외).
- **[SSD — Top 10 Financial Ratios for Successful Dividend Investing](https://simplysafedividends.com/world-of-dividends/posts/32-top-10-financial-ratios-for-successful-dividend-investing)**
  및 다수 문헌: 배당성향 60% 이하를 안전 상한으로 공통 언급.
- **FCF 대비 배당 비율**: [Odalite](https://www.odalite.com/blog/free-cash-flow-dividend-sustainability),
  [AlphaExCapital](https://www.alphaexcapital.com/stocks/stock-investing-strategies/dividend-investing-strategies/payout-ratio-safe-levels)가
  "60~70% 이하 안전"에 수렴. [Equicurious](https://www.equicurious.com/learn/equities/equity-income-and-dividends/free-cash-flow-tests-dividend-safety)는
  "FCF 커버리지 1.5배(≈67%)가 실무적 하한"이라고 표현 — 같은 지점을
  가리킴. earnings 기반 배당성향과 달리 FCF 기반은 "회계상 이익은
  조작 여지가 있어도 실제 현금흐름은 못 속인다"는 점에서 더 신뢰받는
  지표로 여러 출처가 강조함.
- **ROE — 워런 버핏 기준**: [Forbes](https://www.forbes.com/sites/investor/2020/05/29/14-return-on-equity-champions-with-warren-buffett-fundamentals/),
  [Trustnet](https://www.trustnet.com/investing/13445255/warren-buffetts-criteria-for-selecting-stocks):
  "15% 이상을 여러 해에 걸쳐 유지하면 우량, 20%+는 예외적으로 뛰어남."
- **부채비율(D/E)**: [Winvesta](https://www.winvesta.in/blog/investors/debt-to-equity-ratio-assessing-financial-risk-in-stocks),
  [Vested Finance](https://vestedfinance.com/blog/us-stocks/debt-to-equity-ratio-meaning-formula-sector-benchmarks-risk-factors/):
  "1.0배 이하 보수적, 1.0~2.0배는 대부분 업종에서 수용 가능, 넘어서면
  주의." 업종별 차등(테크 <0.5, 유틸리티 <2.0 등)까지는 이번 범위에서
  반영하지 않음(업종 분류 데이터 미수집, 아래 "남은 위험" 참고).
- **이자보상배율**: [eCapital](https://ecapital.com/financial-term/minimum-interest-coverage-ratio/)
  등 코퍼레이트 파이낸스 통설 — "1.5배가 일반적 최소 허용선, 3배 이상이면
  안전권"(대출 코버넌트 관행도 통상 2~3배 요구). 기획서가 인용했던
  "5배 이상"보다 더 널리 인용되는 경계로 대체했다.
- **참고(채택 안 함)**: Benjamin Graham의 방어적 투자자 기준(current
  ratio ≥2.0, 장기부채<순유동자산)도 찾아봤지만, 이건 "총부채/자기자본"
  형태의 D/E가 아니라 순유동자산 기준의 훨씬 엄격한 별도 지표라 이번
  5개 지표 체계에 그대로 끼워 넣기 어려워 채택하지 않았다.

## 발견한 설계 결함 — 부채비율 계산식 정정

원래(내가 처음 짰던) 부채비율은 `docs/decisions/12`의 표기를 그대로 따라
`totalLiabilities ÷ totalShareholderEquity`(총부채÷총자본)이었다. 그런데
위에서 찾은 0.5~2.0배 같은 D/E 임계값은 **이자부담부채(차입금)만** 분자로
쓸 때의 기준이다. `totalLiabilities`는 매입채무·이연수익 등 영업상
부채까지 다 포함해서 이자부담부채보다 항상 훨씬 크다 — 실제로 KO의
`totalLiabilities`(705.41억 달러)÷`totalShareholderEquity`(321.69억
달러)=2.193배로, 이 잘못된 정의를 웹 조사로 찾은 진짜 D/E 임계값(1.0~2.0)에
그대로 적용했다면 KO 같은 정상적인 배당 우량주도 "위험"으로 나왔을 것이다
(`docs/decisions/12`의 "부채비율 219.3%"이 바로 이 잘못된 정의로 계산된
값이었다 — 그 문서는 "계산 가능하다"만 확인했지 정의가 맞는지는 검증
안 했었다).

**정정**: 분자를 Alpha Vantage BALANCE_SHEET의 `shortLongTermDebtTotal`
(단기+장기 이자부담부채를 이미 합산해 제공하는 필드 — `shortTermDebt`+
`longTermDebt`를 직접 더한 값과 정확히 일치하지 않아서, Alpha Vantage가
제공하는 이 필드를 그대로 신뢰하기로 함)로 바꿨다.

## 실측 확인 (KO, 2026-08-25 실키로 재확인)

| 필드 | 값 |
|---|---|
| OVERVIEW.ReturnOnEquityTTM | 0.42 |
| OVERVIEW.DividendPerShare | 2.08 |
| OVERVIEW.EPS | 3.33 (최초 확인 시점) → 3.37 (구현·수집 시점) |
| BALANCE_SHEET.shortLongTermDebtTotal (2025-12-31) | 47,214,000,000 |
| BALANCE_SHEET.totalShareholderEquity | 32,169,000,000 |
| CASH_FLOW.operatingCashflow (2025-12-31) | 7,408,000,000 |
| CASH_FLOW.capitalExpenditures | 2,112,000,000 |
| CASH_FLOW.dividendPayout | 8,779,000,000 |
| INCOME_STATEMENT.ebit (2025-12-31) | 17,652,000,000 |
| INCOME_STATEMENT.interestExpense | 1,654,000,000 |

모든 필드가 null 없이 채워져 있었다(퍼센트류는 전부 소수 형태 문자열,
`"0.42"`처럼 — `docs/decisions/12`가 남긴 "퍼센트 표기 방식 불확실"
위험은 해소됨). **EPS는 설계 단계와 실제 수집(`IngestionRunner`) 사이
몇 시간 만에 3.33→3.37로 값이 바뀌었다** — 코드 버그가 아니라 Alpha
Vantage가 TTM EPS를 주기적으로 재계산해서 내려주기 때문으로 보인다
(재확인 직접 호출로 3.37이 그 시점의 실제 값임을 확인함). "재무제표
스냅샷은 시점에 따라 달라질 수 있다"는 걸 보여주는 사례라 남겨둔다 —
`FinancialFundamentals`가 티커당 1행만 유지하는 "최신 스냅샷" 설계인
이유이기도 하다.

## 계산식 (5개 지표, 각 20점, 선형보간, 합계 0~100)

| 지표 | 계산식 | 20점(만점) | 0점 |
|---|---|---|---|
| 배당성향 | DPS÷EPS | ≤60% | ≥100% |
| FCF 대비 배당 | dividendPayout÷(OCF−Capex) | ≤70% | ≥100% |
| ROE | ReturnOnEquityTTM | ≥15% | ≤0% |
| 부채비율(D/E) | shortLongTermDebtTotal÷totalShareholderEquity | ≤1.0배 | ≥2.0배 |
| 이자보상배율 | EBIT÷이자비용 | ≥3.0배 | ≤1.5배 |

값이 만점 경계보다 안전하면 20점, 위험 경계보다 나쁘면 0점으로 클램프한다.
5개 원자재 값 중 하나라도 없으면 점수 자체를 계산하지 않는다(부분 점수는
다른 종목과 비교 불가능해져 오해를 부를 수 있음).

**밴드**: Simply Safe Dividends가 공개한 5단계 경계를 3색 신호등으로
압축 — **GREEN ≥61 / YELLOW 41~60 / RED ≤40**.

## KO 실측값으로 검증한 결과

실제로 `IngestionRunner`를 돌려 DB에 저장한 뒤 `GET /api/tickers/KO/safety-score`로
받은 응답이며(EPS=3.37 기준), 별도 Python 스크립트로 독립 재계산해 정확히
일치함을 확인했다:

- 배당성향 = 2.08÷3.37 = 61.72% → 19.14점
- FCF 대비 배당 = 8,779,000,000÷(7,408,000,000−2,112,000,000) = 165.77% → 0점(클램프)
- ROE = 42% → 20점(클램프)
- D/E = 47,214,000,000÷32,169,000,000 = 1.47배 → 10.65점
- 이자보상배율 = 17,652,000,000÷1,654,000,000 = 10.67배 → 20점(클램프)
- **총점 = 69.79 → 70점 → GREEN**

(참고: 잘못된 부채비율 정의로 계산했다면 66점대·YELLOW가 나왔을 것 —
이번 정정이 실제로 결과를 바꾸는 유의미한 수정이었음을 확인. 단위
테스트(`DividendSafetyScoreCalculatorTest`)는 설계 검증용으로 EPS=3.33
시점의 손계산 예시를 그대로 쓴다 — 총점 68.75/GREEN, 값 자체가 살짝
다르지만 공식 검증 목적엔 지장 없다.)

## 남은 위험

- 업종별 차등을 반영하지 않는다 — 예를 들어 유틸리티는 구조적으로 D/E가
  높아도 안전하다고 보는 게 통설인데, 이번 5개 지표엔 업종 정보가 없어
  전 종목에 같은 임계값을 적용한다. 브랜드 풀 8종목이 업종이 다양해지면
  (유틸리티·리츠 등) 왜곡될 수 있다 — 그때 업종별 가중치 도입을 재검토.
- 가중치(5개 지표 동일 20점)는 균등 가중이 맞다는 근거자료는 못 찾았다
  (Simply Safe Dividends도 실제 가중치는 비공개). 균등 가중은 "다르게
  줄 근거가 없어서 균등하게 뒀다"는 소극적 선택이지, 최적이라고 검증된
  건 아니다.
- 이번 실측은 KO 1종목만 했다. 브랜드 풀 나머지 종목에서 필드 결측이나
  이상치(예: 적자 기업의 EPS 음수 → 배당성향 계산이 음수가 되는 경우)가
  있는지는 구현 시 전체 브랜드 풀로 재확인이 필요하다.
