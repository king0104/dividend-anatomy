# 07. 환율 데이터도 Twelve Data — 새 제공자 계약 없이 기존 클라이언트 재사용

**날짜**: 2026-08-23

## 왜 확인했나

PROJECT.md 5.6절(환율)을 구현하려면 "배당 지급일 기준" 과거 일별
USD/KRW 환율이 필요하다. 새 제공자를 붙이기 전에, 이미 계약돼 있고
가격 시계열에 쓰는 Twelve Data(`docs/decisions/01-data-source.md`)가
forex 페어도 지원하는지부터 실제 호출로 확인했다 — 지원한다면 새
계약·새 ToS 검토 없이 기존 `TwelveDataClient`를 그대로 재사용할 수
있다.

## 확인 방법과 결과

실제 발급된 토큰으로 `GET /time_series?symbol=USD/KRW&interval=1day&...`를
직접 호출:

```
curl "https://api.twelvedata.com/time_series?symbol=USD/KRW&interval=1day&start_date=2026-08-01&end_date=2026-08-22&apikey=..."
→ status: "ok", meta.type: "Physical Currency", 종가 시계열 정상 반환
```

응답 스키마가 주가 조회 때와 **완전히 동일**하다(`meta`/`values[].datetime,close`/`status`)
— 기존 `TwelveDataTimeSeriesResponse`, `TwelveDataBar` DTO를 그대로
재사용 가능하고, `TwelveDataClient.fetchDailyTimeSeries(symbol, start, end)`도
`symbol="USD/KRW"`를 넘기기만 하면 코드 변경 없이 그대로 동작한다.

### 커버리지 한계 확인

```
start_date=2003-01-01, end_date=2003-01-15 → "No data is available on the specified dates"
start_date=2003-01-01, end_date=2026-08-22 → 정상 응답, 5000건, 실제 최초 날짜=2007-10-11
```

- **실제 USD/KRW 일별 데이터는 2007-10-11부터 존재한다.** 그 이전
  (KO 배당 이력은 2003년부터 있음)은 이 제공자로 확보 불가.
- **한 번 호출로 최대 5000건**(free 플랜 상한으로 보임) — 2007-10-11~2026-08-22는
  5000건을 넘어서므로 최소 2회 이상 나눠서 수집해야 전체 커버.

## 결정

- **새 제공자를 붙이지 않고 Twelve Data를 그대로 쓴다.** 가격 파이프라인의
  `TwelveDataClient`/`TwelveDataTimeSeriesResponse`/`TwelveDataBar`를
  전혀 수정하지 않고 재사용 — 새로 짜야 하는 건 매퍼(`ExchangeRate`로
  변환)와 저장 엔티티/리포지토리/수집 서비스뿐이다.
- **2007-10-11 이전 배당 지급 건은 환율 데이터가 원천적으로 없다.**
  이걸 에러로 막지 않고 "환율 데이터 없음"으로 명시적으로 표시한다
  (CLAUDE.md "데이터 불완전 시 조용히 넘어가지 않는다" —
  [[time-series-integrity-logging]]과 같은 원칙). KO 94건의 배당 중
  2007-10-11 이전 지급 건(2003~2007년, 약 18건)은 원화 환산이 항상
  "데이터 없음"으로 나올 것으로 예상 — 버그가 아니라 실제 데이터
  한계다.
- **수집은 2회 이상 나눠서 수동 호출한다.** 자동 청크 분할 로직은
  만들지 않는다 — 기존 가격/배당 수집도 스케줄링 없이 수동
  트리거이므로(`docs/progress-log.md` "아직 안 끝난 것" 항목과 동일),
  이 프로젝트 범위에서 일관된 선택이다.

## 남은 위험

- Twelve Data의 forex 데이터가 실제로 어느 시각 기준 종가인지(뉴욕
  종가? UTC 자정?) 문서화가 명확하지 않다 — "배당 지급일 기준"이라는
  원칙이 요구하는 정밀도(일 단위)에서는 문제가 되지 않는다고 판단했지만,
  분 단위로 더 정밀한 걸 요구하게 되면 재검토 필요.
- 무료 플랜 일일 800콜 한도(`docs/decisions/01-data-source.md`)를
  가격+배당+환율 수집이 같이 나눠 써야 한다 — 지금은 전부 수동
  트리거라 문제가 안 되지만, 나중에 스케줄링을 붙이면 재검토 대상.
