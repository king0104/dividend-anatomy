# 04. 인접한 두 TTM 창(t0, t1)이 경계일에서 겹쳐서 테스트 데이터가 깨짐

**날짜**: 2026-08-23

## 무슨 일이 있었나

`YieldDecompositionServiceTest.reproducesHandCalculatedYieldChangeDecomposerCase`
에서 `t0`(=`t1.minusYears(1)`) 창에 배당 4건, `t1` 창에 배당 4건을
시드했는데, `t0` 창의 마지막 지급일을 `t0` 그 날짜 자체로 넣었더니
실행 시 예외가 났다:

```
java.lang.IllegalArgumentException: foundCount(5)는 0 이상 expectedCount(4) 이하여야 한다
    at com.dividendanatomy.domain.yield.TtmDividendSummary.<init>(TtmDividendSummary.java:21)
```

## 왜 놓칠 뻔했나

`TtmDividendAggregationService.summarize()`의 창은 `[windowEnd-12개월,
windowEnd]`로 **양 끝 포함**이다(스펙 요구사항 그대로). `t0`
창은 `[t0-12개월, t0]`, `t1` 창은 `[t1-12개월, t1]` = `[t0, t1]`(정확히
1년 차이이므로). 두 창 모두 `t0`라는 날짜를 포함한다 — 즉 `t0` 그 날에
지급된 배당은 **두 창 모두에 걸린다.** `t0` 창 전용으로 배당 4건을
채우고 `t0` 자체를 그중 하나로 썼다는 건, 그 배당이 `t1` 창에도
포함된다는 걸 계산에 안 넣은 것 — 창 정의(양 끝 포함)를 테스트 데이터
설계에 그대로 반영하지 못했다.

## 어떻게 잡았나

`TtmDividendSummary`의 생성자 불변식(`foundCount`는 `expectedCount`를
넘을 수 없음 — Day 2 계획 단계에서 미리 넣어둔 방어 코드)이 실행 즉시
예외를 던져서 바로 알아챘다. 예외 메시지에 `foundCount(5)`가 찍혀
있어서, "5건이 잡혔다"는 사실에서 "어딘가 하나가 중복으로 잡혔다"는
걸 역추적했고, 창 경계가 겹치는 지점(`t0`)을 확인했다.

## 어떻게 고쳤나

프로덕션 코드(`TtmDividendAggregationService`, `TtmDividendSummary`)는
그대로 두고, 테스트 데이터만 수정했다 — `t0` 창의 마지막 지급일을
`t0` 대신 `t0.minusDays(1)`로 옮겨서 두 창의 경계가 안 겹치게 했다.

```java
// Before
saveDividend(ticker, t0, "0.75");
// After
saveDividend(ticker, t0.minusDays(1), "0.75");
```

## 교훈

- **"양 끝 포함" 창을 두 개 인접하게(정확히 1년 차이) 쓰면, 경계일에
  지급이 있는 경우 두 창 모두에 걸린다.** 이건 버그가 아니라 창 정의의
  자연스러운 결과다 — `summarize(t0)`와 `summarize(t1)`은 서로 독립적인
  호출이라 "중복 계산하면 안 된다"는 규칙 자체가 없다. 테스트 데이터를
  짤 때 이 사실을 놓치기 쉽다.
- **더 중요한 잠재적 위험**: `TtmDividendSummary`가 `foundCount >
  expectedCount`를 예외로 막아뒀는데, 실제 데이터에서도 지급 주기가
  살짝 어긋나면(예: 분기 배당이 정확히 91~92일 간격이고 캘린더가
  맞아떨어지는 해) **롤링 366일 창에 분기 배당이 5번 들어가는 경우가
  실제로 있을 수 있다.** 지금은 이 경우 예외로 죽는다 — 실제 배치
  데이터로 이 상황이 나오면 그때 가서 `expectedCount`를 못 넘게 막는
  불변식 자체를 완화할지 다시 판단해야 한다. 지금은 의도적으로 그대로
  둔다(추측성 수정 안 함).

## 후속 (2026-08-23) — 실제로 터짐

여기서 "실제 데이터에서도 있을 수 있다"고 남겨둔 위험이 삭감 이력
탐지 지표를 KO 실데이터로 검증하는 중 그대로 재현됐다: KO의
2004-03-11과 2005-03-11 지급이 정확히 12개월 차이라 `foundCount(5) >
expectedCount(4)`로 400 에러가 났다. 이번엔 창 정의 자체를
"양 끝 포함"에서 "시작점 제외, 끝만 포함"으로 바꿔서 구조적으로
해결했다 — `docs/decisions/05-ttm-window-boundary-fix.md` 참고.
