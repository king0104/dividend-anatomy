# 진행 기록

PROJECT.md 10장 일정 대비 실제 진행 상황을 남긴다. 세부 근거는 각 항목이
가리키는 `docs/decisions/`, `docs/ai-predictions.md`, `docs/ai-defects/`에
있고, 이 문서는 그것들을 하나로 모아 보여주는 목차 겸 요약이다.

---

## Day 1 (2026-08-22 ~ 2026-08-23)

커밋 20개. PROJECT.md 10장이 요구한 Day 1 항목(API 확인, 저장소, CLAUDE.md,
Hooks, ArchUnit, 커밋 규칙)을 전부 마쳤다.

### 1. 데이터 제공자 확정 — 가장 오래 걸린 부분

처음 계획(Finnhub 단일 제공자)이 실제로 검증하자마자 깨졌고, 그 뒤로
5개 제공자를 오가며 최종 조합에 도달했다. 전체 과정: [`docs/decisions/01-data-source.md`](decisions/01-data-source.md).

| 제공자 | 결과 | 최종 용도 |
|---|---|---|
| Finnhub | 배당·주가 모두 403 (무료 플랜 차단) | 제외 |
| Massive (구 Polygon.io) | 배당·분할 됨(24년), 주가는 2년으로 조용히 축소 | **배당·분할** |
| Twelve Data | 주가 6년 됨, 배당·분할 유료 전용. **공개 배포 자체가 약관상 금지** 확인 | **주가 시계열** |
| Alpha Vantage | 데모 키론 다 되는 듯 보였으나 실키론 주가 4.7개월뿐(최악). 배당·분할은 우수 | 채택 안 함(중복) |
| Stooq | ToS 확인 전에 봇 차단 챌린지로 접근 자체 불가 | 제외 |

**중요한 판단**: Twelve Data는 "공개 배포 불가"가 이미 확인된 상태인데도,
프로젝트 완성을 우선하기로 하고 **알면서 리스크를 감수**하기로 결정했다.
이유와 리스크 평가는 `01-data-source.md`와 `00-ai-harness.md`에 기록.
나중에 문제가 생기면 어댑터만 교체하면 되는 구조([CLAUDE.md](../CLAUDE.md)의
"서비스 계층은 DB만 읽는다" 원칙)라는 게 이 판단의 전제였다.

**부수적으로 확인된 것**: 2024~2026년 2년 창만으로는 PROJECT.md의 핵심 예시
("배당률 급등 = 사실 주가 폭락")가 재현되지 않는다는 걸 배당킹 10종목 실증
스캔으로 확인 — 그래서 3~5년 확보가 실질적으로 중요하다는 근거가 생겼다.

### 2. 저장소 세팅

- `CLAUDE.md`: BigDecimal 강제, DB-only 서비스 계층, 테스트 우선, 투자
  조언 금지, T+1 배당락일, 세금/환율 규칙, Spring Boot 4.1 함정 등
  — 근거는 [`docs/decisions/00-ai-harness.md`](decisions/00-ai-harness.md)
- `docs/ai-predictions.md`: 구현 전 AI 실패 예측 6개 (T+2 규칙, 특별배당
  합산, double 사용, 분할 단위 불일치, 세율 혼동, Spring Boot 3.x 관성)

### 3. Java/Spring Boot 뼈대

- Spring Boot 4.1.1 (Spring Boot 3.5가 2026-06-30 EOL이라 4.1이 신규
  프로젝트 표준), Java 21, Gradle 9.5.1
- DB는 **MySQL** — PostgreSQL을 먼저 검토했으나(근거: 최신 예제 편향 회피,
  JSONB 성숙도, 거버넌스), 사용자의 실사용 숙련도가 더 강한 근거라고 판단해
  뒤집음. 이후 "금융 도메인이라 Postgres가 낫지 않나", "익숙함 빼고 봐도
  괜찮나" 두 차례 재검토했으나 둘 다 MySQL 유지로 결론. 전체 논의:
  [`docs/decisions/database.md`](decisions/database.md)

### 4. ArchUnit

domain 패키지에서 `double`/`float` 금지 규칙 3개(필드/반환타입/파라미터).
구현 중 실제 버그를 잡았다 — `noCodeUnits().should(커스텀조건)`이 이벤트를
내부적으로 반전시켜 평가한다는 걸 몰라서 위반이 조용히 통과했던 사례.
직접 위반 클래스를 만들어 검증하다가 발견. 기록:
[`docs/ai-defects/01-archunit-event-inversion.md`](ai-defects/01-archunit-event-inversion.md)

### 5. Hooks

`.claude/settings.json`에 커밋:
- `PostToolUse`(`Write|Edit`, `.java`만) → `./gradlew compileJava -q`
- `Stop` → `./gradlew test -q` (`async: true`)

jq가 환경에 없어서 python3로 우회했다가, 사용자 요청으로 jq를 설치(brew의
mongodb 탭 신뢰 문제를 일회성으로 우회)하고 정식 jq 기반으로 교체.
**주의**: 이번 세션에는 아직 미활성 — `.claude/`가 세션 시작 시점엔 없어서
워처가 못 봄. `/hooks` 재실행이나 새 세션에서 활성화됨.

### 참고 — 아직 안 끝난 것

- Massive의 개인 프로젝트 공개 배포 가능 여부는 여전히 미확인 (의도적으로
  보류, `01-data-source.md` 참고)
- `docs/ai-defects/`에 이번 것 1건만 있음 — `/defect` 커맨드는 아직 안 만듦

---

## Day 2 (2026-08-23)

커밋 5개. Day 1에서 세운 통제 장치(스펙 우선, 사전 예측, ArchUnit,
Hooks)가 실제로 AI 실수를 막거나 잡아내는지 이번 Day 2에서 전부 실전으로
확인됐다.

### 1. 슬래시 커맨드 + workflow.md

`.claude/commands/spec.md`, `defect.md`, `verify.md` 3개와
[`docs/workflow.md`](workflow.md)(지표 추가 8단계) 작성. 이후 모든 작업이
이 8단계(스펙 → 예측 → 테스트 → plan → 구현 → 검증 → 기록 → `/clear`)를
그대로 따랐다.

### 2. 배당락일 T+1 규칙 — SEC 원문 확인

CLAUDE.md에 상식 수준으로만 적어뒀던 규칙을 SEC 원문(Release No.
34-99871, File No. SR-NYSE-2024-19)으로 직접 검증. SEC.gov 자체는
자동화 요청을 rate-limit(403)으로 막아서, federalregister.gov의
public-inspection PDF를 직접 파싱해 원문 텍스트를 확보했다. 결론:
**2024-05-29 기준일부터 배당락일 = 기준일(같은 날)**, 그 이전은 구
규칙(기준일 1영업일 전) 유지. [`docs/decisions/02-ex-dividend-t1-rule.md`](decisions/02-ex-dividend-t1-rule.md)

### 3. 배당수익률 변화 기여도 분해 — 이 프로젝트의 핵심 지표

`/spec` → 예측 → Plan Mode → 구현 → 테스트 8단계를 그대로 실행한 첫
사례. [`docs/specs/yield-change-decomposition.md`](specs/yield-change-decomposition.md)에서:

- 계산식: 대칭(평균)법 기여도 분해 — 순차법(어느 쪽을 먼저 고정했는지에
  따라 결과가 달라지는 임의성)을 피하려고 닫힌 형태 수식을 직접 유도해
  스펙에 못박음
- 기준 시점: 롤링 1년 전 대비 (사용자 확인)
- TTM 배당 구멍 처리: 실제합산·연환산 두 값 다 노출 (사용자 확인)
- 반올림: 소수 2자리 HALF_UP, 중간 계산은 무반올림 (사용자 확인)

구현 결과(`YieldChangeDecomposer` 등)는 손계산 케이스 2개(라운드 넘버 +
이 프로젝트의 플래그십 시나리오 "배당은 그대로인데 주가만 폭락") 포함
테스트로 검증. 사전 예측 #7~9(순차법 오적용, 0으로 나누기, actual/
annualized 혼동)는 **전부 빗나갔다** — 스펙에 닫힌 형태 수식과 불변식을
미리 못박아둔 덕분. 대신 예측 못 한 문제(BigDecimal `MathContext` 반올림
경로 차이로 항등식 테스트 실패)를 실제로 겪음 —
[`docs/ai-defects/02-mathcontext-precision-drift.md`](ai-defects/02-mathcontext-precision-drift.md).
"스펙을 미리 쓰면 예측했던 실수가 실제로 안 일어난다"는 걸 보여준 사례.

### 4. 가격·배당 데이터 영속성 계층

계획을 세우던 중, 예측 #4("주식 분할 전후 배당금 단위 불일치를 무시할
것")가 실제로 벌어지는 문제라는 걸 **실제 API 호출 2건**으로 확인했다:

- Massive 배당(KO, 2012-08-13 2:1 분할): 분할 직후 배당이 정확히 절반
  (`$0.51`→`$0.255`) — **raw, 분할 미조정**
- Twelve Data 가격(NVDA, 2024-06-10 10:1 분할): 분할 전후 가격이 연속적
  — **이미 split-adjusted**

가격과 배당의 "주식 수 기준"이 서로 다르다는 뜻. 자세한 내용과 결정은
[`docs/decisions/03-split-adjustment.md`](decisions/03-split-adjustment.md).
`DividendPayment`는 raw 그대로 저장하고 `SplitEvent`를 별도 테이블로
분리해서, 분할 조정 계산 자체는 다음 증분(TTM 집계 서비스)으로 미뤘다.

`Ticker`/`PriceBar`/`SplitEvent`(`domain.market`),
`DividendPayment`/`DividendType`(`domain.dividend`) 엔티티 + Spring Data
JPA 리포지토리 4개. "가장 가까운 값" 가격 조회 원칙을
`findTopByTickerAndDateLessThanEqualOrderByDateDesc` 메서드 이름 쿼리로
구현하고 `@DataJpaTest`+H2로 검증(9개 테스트).

이 과정에서 예측 #6도 실제로 적중: `@DataJpaTest`를 Boot 3.x 패키지로
import했다가 컴파일 에러 — Boot 4.1에서 `org.springframework.boot.data
.jpa.test.autoconfigure`로 이동한 걸 놓침.
[`docs/ai-defects/03-datajpatest-package-moved.md`](ai-defects/03-datajpatest-package-moved.md)

### 참고 — 아직 안 끝난 것

- 분할 조정 계산(raw 배당 ÷ 이후 발생한 분할 비율 누적곱)과 TTM 집계
  서비스 — `YieldChangeDecomposer`와 DB를 실제로 연결하는 부분. 다음
  증분으로 계획만 세워둠.
- PROJECT.md 일정상 Day 3~4는 원래 "빈 껍데기 배포"인데, 계산 로직·DB
  설계를 먼저 진행함 — 배포를 너무 늦추면 일정표의 위험 요소("마지막
  날 배포하려다 실패")가 그대로 재현될 수 있어 다음 판단 필요.
- Massive ToS 미확인 상태는 Day 1과 동일하게 유지 (의도적 보류).

---

## Day 3 (2026-08-23)

커밋 4개. 계산 로직·DB·실제 외부 API가 전부 이어져서, 이 시점부터는
"시드 데이터로 검증한 계산"이 아니라 "실제로 채워질 수 있는 데이터
파이프라인"이 됐다.

### 1. 분할 조정 TTM 집계 서비스

`TtmDividendAggregationService`가 raw 배당을 `SplitEvent` 누적 비율로
나눠 현재 주식 수 기준으로 환산하고, `YieldDecompositionService`가
티커 조회 → 가장 가까운 가격 조회 → TTM 집계 → `YieldChangeDecomposer`
호출을 엮는다. 실제 KO 분할 데이터로 조정 전(1.53)/후(1.02) 차이를
테스트로 검증. 테스트 데이터에서 인접한 두 TTM 창(정확히 1년 차이)이
경계일에 겹쳐서 `foundCount`가 예상보다 1 많이 잡히는 걸 겪음 —
`TtmDividendSummary`의 불변식이 바로 잡아줬다
([`docs/ai-defects/04`](ai-defects/04-ttm-window-boundary-overlap.md)).

### 2. 실제 데이터 수집 어댑터 — 가격 + 분할

`ingestion.twelvedata`(가격), `ingestion.massive`(분할) 패키지. `RestClient`
기반 얇은 클라이언트 + 순수 매퍼(이번 세션에서 실제로 캡처한 NVDA
JSON을 테스트 리소스로 그대로 사용) + 저장 서비스. 가격은 재수집 시
갱신(Twelve Data가 분할 후 과거 종가를 재조정할 수 있어서), 분할은
이미 있으면 건너뜀.

이 과정에서 `spring-boot-starter-webmvc`에 `RestClient` 자동구성이
딸려올 거라 가정했다가 전체 컨텍스트 테스트가 깨짐 — Boot 4.1은
`spring-boot-starter-restclient`가 별도 스타터. **Boot 4 스타터 분리
패턴(예측 #6)이 이걸로 세 번째 적중**
([`docs/ai-defects/05`](ai-defects/05-restclient-starter-missing.md)).

### 3. 실제 데이터 수집 어댑터 — 배당, 그리고 큰 발견

배당 수집 어댑터를 만들기 직전, Massive 배당 응답을 다시 자세히 보니
`dividend_type`, `frequency` 필드가 있었다. COST의 실제 2023-12-27
$15 특별배당으로 확인한 결과: 정기 배당은 `dividend_type=CD,
frequency=4`, 특별배당은 `dividend_type=SC, frequency=0`으로 이미
구분돼서 내려온다.

**이게 Day 1 예측 #2("특별배당 구분 로직이 자명하지 않음")와 CLAUDE.md가
전제했던 "정기 주기 자체 추론" 문제를 통째로 해결한다** — 자체 분류
알고리즘을 만들지 않고 제공자 필드를 신뢰하기로 결정
([`docs/decisions/04-dividend-classification.md`](decisions/04-dividend-classification.md)).
다만 `CD`/`SC` 외 코드를 만나면 조용히 REGULAR로 넘기지 않고 예외를
던지게 해서, 제공자가 우리가 모르는 값을 주면 바로 드러나게 했다.

`MassiveDividendIngestionService`가 배당 저장과 동시에
`Ticker.regularPaymentsPerYear`를 `frequency` 필드로 갱신 — 더 이상
수동 설정 값이 아니다.

### 참고 — 아직 안 끝난 것

- 컨트롤러/API 계층, 스케줄링(자동 수집 트리거) 없음 — 서비스 메서드를
  수동으로만 호출 가능.
- `MassiveClient`/`TwelveDataClient`의 URL/쿼리 파라미터 조립 자체는
  자동 테스트 범위 밖(Boot 4의 RestClient 테스트 슬라이스 지원 모듈을
  못 찾아서 매퍼/서비스 계층 검증에 집중함) — 실제 API 호출로 수동
  검증만 함.
- Day 3~4 "빈 껍데기 배포"는 아직 안 함 — 계산 엔진·데이터 파이프라인
  쪽을 먼저 진행 중.
- Massive ToS 미확인 상태 유지(의도적 보류).

---

## Day 4 (예정)

- 컨트롤러/API 계층, 또는 Day 3~4 "빈 껍데기 배포" — 우선순위 재확인
  필요
- 스케줄링/실행 트리거
- README, 지표 확대(성장률 둔화, 삭감 이력 등)
