# 시계열 정합성 검증 — 로그 (Time-Series Integrity Logging)

PROJECT.md 5.4절. "감지 → 로그 → 화면에 '데이터 불완전' 표시. 조용히
넘어가지 않는다"의 세 단계 중 **감지·화면 표시는 이미 부분 구현돼
있고, 로그만 코드베이스 전체에서 0%**다(Logger 자체가 없음). 이
스펙은 새 계산 로직을 만들지 않는다 — 기존 감지 지점에 로그 한 줄씩
추가하는 계측(instrumentation) 작업이다.

## 0. 스코프

- **새 데이터 구멍 감지 로직을 만들지 않는다.** `TtmDividendSummary`의
  `foundCount < expectedCount`(불완전 창)는 이미 3개 지속성 지표
  ([[dividend-growth-deceleration]], [[dividend-cut-detection]],
  [[dividend-volatility]])가 전부 이 경로로 "특정 분기 누락"을
  감지·화면 표시하고 있다 ( `INSUFFICIENT_DATA`/`INCOMPLETE` 상태로).
  별도의 "분기 누락 전용 감지기"를 새로 만들면 같은 개념을 두 번
  구현하는 것이라 만들지 않는다.
- 이 스펙이 다루는 건 세 가지 기존 지점에 **로그를 추가**하는 것뿐:
  1. TTM 창 불완전 (기존 감지 로직에 로그 추가)
  2. 배당 금액 분할 조정 적용 (기존 로직에 로그 추가)
  3. 지급일(`payDate`) null 수집 (기존에 감지 자체가 없었음 — 이번에
     감지+로그 둘 다 신규)
- **화면 표시는 이 스펙의 범위 밖이다.** 1·2번은 이미 API 응답에
  상태값으로 노출 중(변경 없음). 3번(`payDate` null)은 화면이 아직
  없어서(Day 10-12 예정) 지금은 로그만 남기고, API 응답 스키마는
  건드리지 않는다(사용자 확인) — 화면 작업 때 다시 결정.
- 순수 도메인 계산기(`DividendVolatilityCalculator`,
  `DividendGrowthCalculator`, `DividendCutDetector`, `NthRootCalculator`
  등)에는 로그를 추가하지 않는다 — 이 클래스들은 프레임워크 의존성
  없는 순수 함수로 설계된 게 의도적인 설계 결정이고(테스트 용이성),
  로깅은 부수효과라 이 원칙과 충돌한다. 대신 이 계산기들이 공통으로
  소비하는 `TtmDividendAggregationService.summarize()` 한 곳에서
  로그를 남긴다 — 3개 지표가 전부 이 메서드를 거치므로 choke point
  하나로 충분하다.

## 1. 로그 지점과 형식 (계산식 대체)

새 계산 없음. 로그 레벨과 위치만 정의한다.

| # | 위치 | 조건 | 레벨 | 메시지에 포함할 정보 |
|---|---|---|---|---|
| 1 | `TtmDividendAggregationService.summarize()` | `foundCount < expectedCount` (불완전 창) | `WARN` | ticker symbol, windowEnd, foundCount, expectedCount |
| 2 | `TtmDividendAggregationService.splitAdjustedAmount()` | `laterSplits`가 비어있지 않음(분할 조정이 실제로 적용됨) | `INFO` | ticker symbol, ex-dividend date, 적용된 누적 분할비율 |
| 3 | `MassiveDividendIngestionService.ingest()` | 매핑된 `DividendPayment.getPayDate() == null` | `WARN` | ticker symbol, ex-dividend date |

- 로거는 각 클래스에 `private static final Logger log = LoggerFactory.getLogger(ClassName.class);` 표준 SLF4J 방식 — Spring Boot가 Logback을 이미 기본 제공하므로 별도 의존성 추가 불필요.
- 1번은 WARN(구멍은 잠재적 데이터 문제), 2번은 INFO(분할 조정은 정상 동작이라 경고가 아니라 가시성 목적).
- 메시지는 사람이 grep 가능하도록 고정 접두어를 쓴다: 예) `"TTM 창 불완전: ticker=%s windowEnd=%s foundCount=%d expectedCount=%d"`.

## 2. 기준 시점

해당 없음 — 계산 로직/기준일 변경 없음. 로그는 각 메서드가 실제로
호출되는 시점(TTM 집계 시, 배당 수집 시)에 그대로 남는다.

## 3. 반올림 방향

해당 없음 — 로그 메시지에 노출하는 값(`foundCount`, `expectedCount`,
분할비율)은 기존 계산 결과를 그대로 문자열화한 것이고 새로운 반올림이
없다.

## 4. 예외 케이스

| 상황 | 처리 |
|---|---|
| TTM 창이 불완전(`foundCount < expectedCount`) | `TtmDividendSummary` 생성 직후 `WARN` 로그(1번). 기존처럼 계산은 계속 진행하고 `isComplete()=false`를 그대로 반환 — 로그 추가가 기존 계산 흐름을 바꾸지 않는다 |
| TTM 창에 캘린더 드리프트로 초과 지급(`foundCount > expectedCount`) | 로그 안 남김 — `isComplete()`가 이미 `true`로 취급하는 정상 케이스([[05-ttm-window-boundary-fix]]), 경고 대상 아님 |
| 배당 지급 이후 실제로 분할이 있었음(`laterSplits` 비어있지 않음) | `INFO` 로그(2번). 조정 자체는 기존 로직 그대로, 로그만 추가 |
| 배당 지급 이후 분할이 없었음 | 로그 안 남김 — 정상 경로, 조정 자체가 no-op |
| Massive 응답의 `payDate`가 null | 매핑 자체는 기존처럼 진행(지급 이력에서 제외하지 않음), `WARN` 로그만 추가(3번). API 응답에는 아직 노출 안 함(사용자 확인, 0절) |
| Massive 응답의 `recordDate`가 null | 스코프 밖 — `payDate`만 다룬다(PROJECT.md 5.4절 원문이 `paymentDate`를 명시). `recordDate`는 T+1 규칙상 `exDividendDate`와 대부분 같아서 결측 영향이 다르고, 별도 근거 없이 같은 처리를 하면 임의 확장이라 이번엔 제외 |
| `payDate`, `recordDate` 둘 다 null | 3번 로그만 남김(`recordDate` null은 위 규칙대로 무시) |

## 5. CLAUDE.md 정합성 체크

- ✅ 서비스 계층은 DB만 읽음 — 로깅은 외부 API 호출이 아니므로 규칙과
  무관
- ✅ 계산 로직 변경 없음 — 순수 도메인 계산기에 부수효과(로깅) 추가
  안 함(0절)
- ✅ 데이터 불완전 시 조용히 넘어가지 않는다 — 이 스펙 자체가 그
  원칙의 "로그" 절반을 채우는 작업
- ✅ 투자 판단/추천 문구 없음 — 로그 메시지는 개발자용 진단 정보일
  뿐, 사용자向 문구 아님
