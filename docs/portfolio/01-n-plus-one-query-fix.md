# 01. N+1 쿼리 제거 — 배당 건별 분할 이력 재조회

**날짜**: 2026-08-25
**관련 커밋**: `c94852d`(수정), `abe9537`(관련 후속 버그 수정)
**포트폴리오 페이지**: https://claude.ai/code/artifact/2f1bfc47-28b2-4a69-95e4-bc6ae3c0d07e (같은 내용의 시각 자료)

## 문제

메인 화면 API(`GET /api/tickers`, [TickerSummaryController.java](../../src/main/java/com/dividendanatomy/web/ticker/TickerListController.java))는 배당킹 57종목의 시가배당률과
연속 배당 증가 연수를 계산해 반환한다. 서비스 계층 코드
([TickerSummaryService.java:97-114](../../src/main/java/com/dividendanatomy/service/ticker/TickerSummaryService.java#L97-L114),
[TtmDividendAggregationService.java:40-62](../../src/main/java/com/dividendanatomy/service/yield/TtmDividendAggregationService.java#L40-L62))를
읽다가, 배당 지급 건 리스트를 `for` 루프로 순회하면서 건마다
`SplitEventRepository`를 다시 호출하는 패턴을 발견했다 — 배당 이후
발생한 주식 분할 이력을 조정 계수로 쓰기 위한 조회였는데, 1번 쿼리로
N건을 가져온 뒤 N번 쿼리로 각 건을 또 조회하는 전형적인 N+1 구조였다.

코드만으로는 "느릴 것 같다"는 감이었다. 실제로 얼마나 나쁜지는
데이터 분포에 달려 있었다 — 이 프로젝트엔 정기 갱신 배치가 없어
운영 DB에 실제로 쌓인 배당 이력 건수(종목당 5~94건)를 확인해야
숫자가 나오는 상황이었다.

## 조치

**실측으로 확인**: OCI Bastion 세션으로 사설 서브넷의 운영 MySQL에
SSH 포트포워딩 터널을 연결해 실제 테이블 규모를 확인했다
(`ticker` 57행, `dividend_payment` 4,671행, `price_bar` 42,811행,
`split_event` 76행). 이어서 Hibernate SQL 로깅
(`spring.jpa.show-sql=true`, `logging.level.org.hibernate.SQL=DEBUG`)을
켠 채로 그 터널 너머 실제 DB에 붙여 `GET /api/tickers`를 1회 호출하고,
로그에 찍힌 쿼리를 테이블별로 집계했다.

**원인**: `SplitEventRepository.findByTickerAndExecutionDateAfterOrderByExecutionDateAsc()`가
`TtmDividendAggregationService.summarize()`와
`TickerSummaryService.calculateStreak()` 두 곳에서 배당 지급 건 리스트를
순회하는 루프 안에 있었다.

**캐싱 대신 쿼리 재구성을 택한 이유**: "요청 간 캐시(TTL·무효화 전략
필요)"와 "요청 내 배치 조회(한 번에 가져와 메모리에서 재사용)"는
다른 선택지다. 후자를 골랐다 — (1) 57종목 정기 갱신 스케줄러가 없어
요청 간 캐시를 넣으면 무효화 타이밍을 수동 갱신에 맞춰 관리해야
하는데 그 복잡도가 불필요했고, (2) 분할 이력이 종목당 최대 23건뿐이라
전체를 한 번에 가져와도 메모리 부담이 없었고, (3) 기존 계산 로직
(`SplitAdjustmentCalculator.adjustedAmount()`)의 입력 타입이 이미
`List<SplitEvent>`라 데이터를 어디서 가져왔는지는 계산 로직이 신경 쓸
필요가 없었다.

**변경**: `SplitEventRepository`에 종목 전체 분할 이력을 한 번에 가져오는
`findByTickerOrderByExecutionDateAsc()`를 추가하고
([SplitEventRepository.java:16-17](../../src/main/java/com/dividendanatomy/repository/SplitEventRepository.java#L16-L17)),
두 서비스에서 배당 건마다 반복 조회하던 부분을 "티커당 1회 조회 + 배당
건별 `Stream.filter`"로 바꿨다. 분할 비율 누적곱 계산식
(`SplitAdjustmentCalculator`)은 그대로 재사용했다 — 데이터 접근 방식만
바뀌었고 계산 로직은 한 줄도 안 건드렸다.

```java
// Before — TtmDividendAggregationService
for (DividendPayment payment : payments) {
  List<SplitEvent> laterSplits = splitEventRepository
    .findByTickerAndExecutionDateAfterOrderByExecutionDateAsc(
      ticker, payment.getExDividendDate());   // payment 건수만큼 DB 왕복
}

// After
List<SplitEvent> allSplits = splitEventRepository
  .findByTickerOrderByExecutionDateAsc(ticker);  // 1회
for (DividendPayment payment : payments) {
  List<SplitEvent> laterSplits = allSplits.stream()
    .filter(s -> s.getExecutionDate().isAfter(payment.getExDividendDate()))
    .toList();
}
```

**검증**: 기존 단위 테스트(`TickerSummaryServiceTest`,
`TtmDividendAggregationServiceTest`)와 전체 스위트가 수정 후 그대로
통과했다. 수정 전/후 `GET /api/tickers` 응답 JSON을 직접 비교해
byte-identical함을 확인해, 계산 결과(시가배당률·연속 증가 연수)가
하나도 안 바뀌었다는 걸 증명했다.

## 결과

같은 방식(운영 DB 대상 실측)으로 수정 전/후를 비교했다.

| 항목 | Before | After |
|---|---|---|
| SELECT 쿼리 수 (요청 1회) | 5,124 | 343 |
| — 그중 `split_event` | 4,895 | 114 |
| 응답 시간 (3회 평균) | 48.11s | 3.61s |
| 응답 JSON | — | Before와 byte-identical |

이후 이 수정을 실제로 GitHub Actions + GHCR 배포 파이프라인
(`docs/decisions/11-cicd-pipeline.md`)으로 운영 클러스터에 배포했고,
실제 서비스 URL(`http://144.24.86.105:30081/api/tickers`)에서 정상
응답(200, 2초대, DB가 같은 VCN에 있어 Bastion 터널 구간이 빠져
로컬 측정치보다 더 빠름)을 확인했다.

## 다음/한계

같은 N+1 패턴이 배당 변동성·배당 삭감 감지·성장 둔화·배당률 분해
화면의 서비스에도 남아 있다 — 전부 `SplitAdjustmentCalculator`를 같은
방식으로 호출한다. 이번엔 실측으로 확인된 메인 화면 경로만 좁혀서
고쳤고, 나머지는 후속 과제로 남겼다.
