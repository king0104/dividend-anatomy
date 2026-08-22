# AI 실패 예측

PROJECT.md 9.A.3: 구현 전에 "AI가 이걸 틀릴 것이다"를 먼저 적어둔다. 사후
회고가 아니라 사전 예측이어야 "도메인을 이해했기에 AI의 실패 지점을 예측할 수
있었다"는 증명이 된다. 결과 칸은 테스트/리뷰로 실제 확인되는 대로 채운다.

| # | 예측 | 근거 | 결과 |
|---|---|---|---|
| 1 | 배당락일을 T+2 시절 규칙(기준일 1영업일 전)으로 구현할 것 | 인터넷에 옛 규칙과 새 규칙이 혼재 (PROJECT.md 6장) | ? |
| 2 | 특별배당을 정기 배당에 합산할 것 | 구분 로직이 자명하지 않음 (PROJECT.md 5.3) | ? |
| 3 | 금액에 `double`을 쓸 것 | 일반적 관성 | ? |
| 4 | 주식 분할 전후 배당금 단위 불일치를 무시할 것 | 데이터에 명시되지 않음 | 맞음 — 실제 API로 확인. Massive 배당은 raw(분할 미조정, KO 2012-08-13 2:1 분할 직후 $0.51→$0.255로 정확히 절반), Twelve Data 가격은 split-adjusted(NVDA 2024-06-10 10:1 분할 전후 가격이 연속적)라 단위가 다름. `docs/decisions/03-split-adjustment.md`. 엔티티 설계(raw 저장 + `SplitEvent` 별도 테이블)로 예방했지만, 조정 계산 자체는 다음 증분에서 구현 예정이라 "무시"가 실제로 일어날지는 그때 다시 확인 필요 |
| 5 | 미국 15%와 한국 15.4%를 혼동할 것 | 두 숫자가 비슷함 | ? |
| 6 | Spring Boot 3.x 시절 API(`spring-boot-starter-web`, Jackson 2 어노테이션, 3.x 시절 Security 설정 등)를 그대로 생성할 것 | 학습 데이터 대부분이 3.x 시절이고, 4.1은 2026-06 출시라 상대적으로 최근이라 학습 비중이 낮음. 스타터 모듈 분리·Jackson 3·Security 7 기본값 변경처럼 겉보기엔 비슷하지만 동작이 다른 지점이 많음 | 맞음 — 리포지토리 테스트 작성 중 `@DataJpaTest`를 Boot 3.x 패키지(`org.springframework.boot.test.autoconfigure.orm.jpa`)로 import해서 컴파일 에러. Boot 4.1에서는 `org.springframework.boot.data.jpa.test.autoconfigure`로 이동. `docs/ai-defects/03-datajpatest-package-moved.md` |
| 7 | 기여도 분해를 대칭(평균)법이 아니라 순차법("가격 먼저 바뀌었다고 가정")으로 구현해서, 결과는 항등식을 만족하지만 어느 쪽을 먼저 고정했는지에 따라 값이 달라지는 임의성을 인지 못 할 것 | 순차 분해가 더 "직관적인" 구현이고, 대칭법은 스펙에 닫힌 형태 수식을 명시해야만 나옴 (`docs/specs/yield-change-decomposition.md` 1.4) | 틀림 — `/spec` 단계에서 닫힌 형태 수식을 직접 명시해뒀기 때문에 구현 시 임의로 고를 여지 자체가 없었음. 스펙을 안 썼다면 이 예측이 맞았을 가능성이 높음 |
| 8 | TTM 배당 지급 횟수 `k(t) == 0`인 경계 케이스에서 0으로 나누거나 NPE를 낼 것 | "지급 이력이 아예 없는 구간"은 흔히 빠지는 경계 케이스 (`docs/specs/yield-change-decomposition.md` 4절) | 틀림 — `decomposeAnnualized()`에서 `foundCount()==0`이면 `Optional.empty()`로 먼저 빠지게 구현했고, `TtmDividendSummary` 생성자 자체가 `foundCount==0`인데 `annualizedSum!=null`인 상태를 막아서 애초에 0으로 나눌 입력이 안 만들어짐. 관련 테스트(`decomposeAnnualized_skipsWhenEitherSideHasNoData`, `ttmDividendSummary_rejectsInconsistentState`) 통과 |
| 9 | `t0`엔 실제합산(`D_actual`), `t1`엔 연환산(`D_annualized`)처럼 두 계열을 섞어서 기여도 분해를 계산할 것 | 같은 계산 함수를 두 시점에 반복 호출하는 구조라 인자를 잘못 짝지을 여지가 있음 | 틀림 — `decomposeAnnualized()`가 `t0.annualizedSum()`/`t1.annualizedSum()`을 짝지어 `decompose()`에 위임하도록 스펙 1.4를 그대로 따라 구현. 다만 예측하지 못했던 다른 문제(BigDecimal `MathContext` 반올림 경로 차이로 항등식 테스트가 실패)를 실제로 겪음 — `docs/ai-defects/02-mathcontext-precision-drift.md` |

- 사후 회고면 "정리했다"에 그치지만, 사전 예측이면 도메인 이해의 증거가 된다
- 맞은 것은 테스트가 잡아준 것, 틀린 것은 "예상보다 AI가 잘하는 영역" — 둘 다
  기록 가치가 있다
