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

## Day 4 (2026-08-23)

커밋 2개(REST API 컨트롤러는 Day 3 끝자락에 이미 커밋했으나 로그에
못 남겨서 여기서 같이 정리). 이 시점부터 "로컬 H2로 검증한 파이프라인"이
아니라 "실제 클라우드 DB + 실제 시장 데이터로 검증한 파이프라인"이 됐다.

### 1. REST API 컨트롤러 (`663ea75`)

`YieldDecompositionController` — `GET /api/tickers/{symbol}/yield-decomposition?asOf=`.
`docs/specs/yield-change-decomposition.md` 3절이 못박은 "%p 단위 소수
2자리 HALF_UP" 반올림을 실제로 적용하는 유일한 지점(`YieldDecompositionResponseMapper`).
서비스 계층 예외(`NoSuchElementException`/`IllegalStateException`/
`IllegalArgumentException`)를 `@RestControllerAdvice`로 404/422/400에
매핑. 구현 중 `HttpStatus.UNPROCESSABLE_ENTITY`가 Spring Framework
7.0에서 `UNPROCESSABLE_CONTENT`로 이름만 바뀐 것(RFC 9110 용어 정렬,
상태 코드는 그대로 422)을 IDE 진단으로 미리 잡아서 테스트 실패 없이
반영 — 별도 `/defect` 기록은 안 남김(실패가 실제로 안 났으므로).

### 2. OCI MySQL Always Free — 실제 데이터를 어디에 담을지

로컬 H2/MySQL 대신 OCI(Oracle Cloud Infrastructure) 무료 티어 DB를
쓰기로 함. 진행하며 나온 판단들:

- **안전장치 논의 → 사용자가 명시적으로 보류.** 처음엔 "AI가 기존
  k3s 클러스터를 절대 건드리지 않고 과금도 안 나게" 하려고 별도 IAM
  사용자/그룹/정책으로 완전히 격리하는 방안을 검토했다(컴파트먼트 분리,
  최소 권한 정책 등). 하지만 실제로 만들기 시작하려는 순간 사용자가
  "안전장치 만들지 말고 그냥 그대로 진행하자, 빨리 만드는 게 중요하다"고
  결정 — 기존 Administrators 키로 곧바로 진행. **이건 의도적으로 채택한
  리스크지, 잊어버린 게 아니다**: 지금 이 세션의 모든 OCI 조작은 관리자
  권한 키로 이뤄졌고, 리소스 삭제/변경을 막는 기술적 장치는 없다. 나중에
  진짜 필요해지면(예: 다른 사람과 같이 쓰게 되면) 별도 IAM 사용자를
  만드는 걸 다시 고려해야 한다.
- **VCN은 기존 k3s와 공유.** 새 VCN을 격리해서 만드는 대신, k3s가 이미
  쓰고 있는 `vcn-20260226`의 private subnet(`10.0.2.0/24`)을 그대로
  재사용 — 나중에 이 프로젝트를 같은 k3s에 배포하게 되면 네트워크가
  이미 붙어있어서 더 간단해진다는 판단.
- **MySQL DB System은 private subnet에만 존재, public IP 없음** (OCI
  MySQL DB System의 일반적 제약 — 확인 완료). 접근은 **OCI Bastion
  Service**의 세션 기반 SSH 포트포워딩으로만: 로컬 `13306` → 터널 →
  DB `10.0.2.201:3306`. Bastion 자체는 상시 VM이 아니라 관리형 서비스라
  Always Free Ampere A1 컴퓨트 할당량(이미 k3s 인스턴스 3대가 다 씀)을
  안 건드리고, 세션은 최대 3시간까지 무료.
- 보안 목록에는 `0.0.0.0/0`이 아니라 **VCN 내부(`10.0.0.0/16`)로만**
  3306 인바운드 규칙 추가 — 기존 k3s 6443 규칙과 같은 패턴.
- MySQL 관리자 비밀번호는 OCI 쪽 검증 규칙(8~32자, 대/소문자·숫자·특수문자
  각 1개 이상)을 몰라서 2번 실패(영숫자만, 그다음 특수문자 빠뜨림)한
  뒤 `oci mysql db-system create --help`에서 정확한 규칙을 찾아
  해결했다.

### 3. 실제 티커로 전체 파이프라인 검증 — KO(코카콜라)

`IngestionRunner`(`--ingest.ticker=SYMBOL:이름:통화`가 있을 때만 동작하는
`ApplicationRunner`, 임시 수동 트리거)로 가격(Twelve Data)·분할·배당
(Massive)을 실제로 수집해 OCI MySQL에 저장:

| 항목 | 결과 |
|---|---|
| 가격(3년치) | 752건 |
| 분할 | 1건 |
| 배당 | 94건, `regularPaymentsPerYear` 자동 4로 설정 |

이후 `GET /api/tickers/KO/yield-decomposition?asOf=2026-08-22`를 실제로
호출해서 **로컬 목데이터가 아니라 실제 클라우드 DB + 실제 시장 데이터로
계산된 결과**(가격 기여도 -0.67%p, 배당 기여도 +0.11%p)를 확인함으로써
이 프로젝트의 핵심 파이프라인(수집 → DB → TTM 집계 → 기여도 분해 →
REST API)이 처음부터 끝까지 실제로 동작한다는 걸 증명했다.

**작은 버그 하나**: `./gradlew bootRun --args="--ingest.ticker=KO:The
Coca-Cola Company:USD"`처럼 공백이 든 문자열을 넘기면 Gradle이 `--args`
값을 공백 기준으로 다시 쪼개버려서 `name` 필드가 "The"로 잘려 저장됨
(금액 계산과 무관한 필드라 지표 자체엔 영향 없음). SQL로 직접 고쳤고,
다음엔 공백 없는 값을 쓰거나 다른 전달 방식을 써야 한다는 걸 기록해둠.

### 참고 — 아직 안 끝난 것

- IAM 격리/예산 알림 같은 실제 과금·오조작 방지 장치는 **의도적으로
  안 만듦** — 위 2번 참고. 여러 명이 같이 쓰게 되거나 장기 운영으로
  넘어가면 다시 검토해야 함.
- `docs/decisions/`에 이번 OCI 인프라 판단(VCN 공유, Bastion, 안전장치
  보류)을 정식 문서로 남기지는 않음 — 지금은 이 진행 기록이 유일한
  기록. 나중에 프로젝트가 커지면 별도 decision 문서로 승격 고려.
- 로컬 SSH 터널 + `gradlew bootRun`은 이 세션이 끝나면 계속 떠 있지
  않음 — 다음에 다시 쓰려면 Bastion 세션을 새로 만들어야 함(세션은
  최대 3시간).
- 스케줄링(자동 수집), README, 지표 확대(성장률 둔화, 삭감 이력 등)는
  여전히 안 함.

---

## Day 5 (2026-08-23)

커밋 7개. 지표 2개(삭감 이력 탐지, 성장률 둔화)를 계산→DB→API까지
끝까지 완성했고, 그 과정에서 실제 데이터로만 드러나는 진짜 버그를
하나 발견해서 고쳤다. 프로세스 쪽에서도 워크플로우에 구멍이 있던
걸 메웠다.

### 1. 배당 삭감 이력 탐지 (`54b6fd1`, `2c917a6`)

`/spec` → 예측 3개(#10~12) → Plan Mode → 구현 → 테스트, 8단계
워크플로우를 그대로 진행. 정기 배당 지급 이력에서 TTM(직전 12개월)
합계가 이전 지급 시점보다 줄어든 구간을 찾는다.

- 계산 기준을 raw 지급액이 아니라 TTM 합계(분할 조정 완료)로 잡아서
  분할·지급주기 변경을 삭감으로 오탐하지 않게 설계
  (`docs/specs/dividend-cut-detection.md`).
- `DividendCutDetector`(순수 계산) + `DividendCutDetectionService`(DB
  연결) + `DividendCutController`(`GET /api/tickers/{symbol}/dividend-cuts`).
- 예측 #10~12(raw 비교 오탐, 완전성 체크 누락, 루프 경계값 실수)
  **전부 틀림** — 스펙 단계에서 이 지점들을 정확히 겨냥한 테스트를
  미리 설계해둔 덕분.

### 2. TTM 창 경계·캘린더 드리프트 버그 — 테스트가 놓친 실제 버그 (`9c11b47`)

삭감 이력 탐지를 실제 KO 데이터로 검증하다가(워크플로우 7단계가
생기게 된 계기, 아래 4번) `foundCount(5) > expectedCount(4)`
예외를 만났다. 원인 규명이 두 단계였다:

1. 1차 진단: 정확히 12개월 차이 나는 두 지급일이 인접한 두 TTM
   창(`[t-12개월, t]`, 양 끝 포함)에 동시에 걸려서 이중 계산됨.
   창을 "시작점 제외, 끝만 포함"으로 바꿔서 수정.
2. 재검증하니 여전히 400 — KO 88개 지급일 전체를 파이썬으로 전수
   시뮬레이션해서 진짜 원인을 찾음: **실제 분기 배당 간격이 91.25일이
   아니라 77~102일로 불규칙**해서, 창 경계 정의와 무관하게 롤링
   12개월 창에 지급이 자연스럽게 5번 들어가는 해가 있음(30개 창에서
   확인). 이건 버그가 아니라 실제 배당 캘린더의 특성.

최종 수정: `TtmDividendSummary`의 `foundCount > expectedCount` 금지
불변식을 없애고(`isComplete()`도 "부족만 불완전"으로 재정의),
`DividendCutDetector`의 비교 기준을 raw `actualSum`에서 지급 횟수
차이를 정규화하는 `annualizedSum`으로 바꿈. 재검증 결과 KO 93건 중
CUT 0/NORMAL 90/INCOMPLETE 3 — 실제로 배당을 삭감한 적 없는 종목이라는
사실과 일치. 전체 과정: `docs/decisions/05-ttm-window-boundary-fix.md`,
`docs/ai-defects/07-ttm-foundcount-surplus-from-calendar-drift.md`.

**교훈**: 손으로 만든 테스트 케이스는 내가 예상한 시나리오만 재현한다 —
59개 테스트가 전부 통과한 상태에서도 실제 22년치 데이터를 전수로
훑어야만 드러나는 버그가 있었다.

### 3. 워크플로우 8단계 → 9단계, "실데이터 검증" 추가 (`6df9b87`, `d5b8322`)

위 2번 사건을 계기로 `docs/workflow.md`에 **7단계: 실데이터 검증**
(테스트 통과 후 서버를 실제로 띄워 손으로 안 만든 진짜 데이터로 최소
1회 확인)을 추가. 이후 성장률 둔화 지표(4번)에서 바로 실전 적용함.

같은 날 사용자가 공유한 우아콘 2025 발표("어제 쓴 프롬프트 오늘도
그대로 쓰시나요?", 임동준)의 "프리프롬프팅" 개념(성공 기준 명확화 →
경험적 반복 검증 → 초안을 버전 올리며 개선, + 켄트 벡의 "불변 테스트")과
이 프로젝트의 기존 장치(`/spec`, `docs/ai-predictions.md`, Plan Mode,
`docs/decisions/`)를 대응시켜 `docs/decisions/06-preprompting-framework-alignment.md`로
정리. 갭도 솔직하게 남김 — 스펙·테스트·구현을 전부 AI가 쓰기 때문에
"AI가 테스트를 못 건드리게 구조적으로 잠그는" 켄트 벡의 핵심 장치가
없다는 것, 프롬프트 리뷰/자산화 문화가 없다는 것. 겸사겸사
`docs/decisions/00-ai-harness.md`의 "도입하지 않은 것과 그 이유" 표가
계속 "(작성 예정)"으로 비어있던 걸 발견해서 실제로 채움.

### 4. 배당 성장률 둔화 (`fe1cbd7`, `8c2d3ca`)

최근 3년 CAGR과 10년 CAGR을 비교해 배당 성장이 둔화되는지 본다. 구현
전 확인한 중요한 제약: `BigDecimal`엔 분수 지수 연산이 없어서 진짜
CAGR(n제곱근)을 구하려면 **뉴턴-랩슨법을 직접 구현**해야 했다
(`Math.pow`/`double` 절대 금지, CLAUDE.md·ArchUnit). 사용자에게 "진짜
CAGR vs 더 단순한 대체 지표(연평균 성장률)" 갈림길을 먼저 확인받고
진짜 CAGR로 진행하기로 결정.

- `NthRootCalculator`(순수 BigDecimal 뉴턴-랩슨, 초기값 1, 허용오차
  1E-15, 최대 100회) — 8의 세제곱근=2, 1.1^10=2.5937424601 등 손계산
  케이스로 검증.
- `DividendGrowthCalculator`는 2번 사건에서 배운 교훈을 곧바로
  적용해서 처음부터 `annualizedSum` 기준으로 구현(raw `actualSum`을
  다시 쓰는 실수를 예측 #15로 미리 경계했고, 실제로도 안 일어남).
- 워크플로우 7단계(실데이터 검증)를 실전 적용하는 과정에서 **Bastion
  세션이 3시간 TTL로 만료되고 로컬 공인 IP도 바뀌어서 터널이 완전히
  끊겨 있던 걸 발견** — 세션 재생성 + `client-cidr-list` 갱신으로
  복구 후 재검증. 실제 KO 데이터: 3년 CAGR≈4.94%, 10년 CAGR≈4.34%,
  `NOT_DECELERATING`(최근이 오히려 더 빠름), 무한루프·예외 없음.
- `GrowthDecelerationController`(`GET /api/tickers/{symbol}/growth-deceleration?asOf=`)까지
  기존 두 컨트롤러와 동일 패턴으로 완성.

### 참고 — 아직 안 끝난 것

- PROJECT.md 5.2절의 배당 지속성 지표 중 **변동성**만 남음(성장률
  둔화·삭감 이력은 완성).
- OCI Bastion 세션은 여전히 3시간마다 수동으로 새로 만들어야 함 —
  자동 재연결 장치 없음(Day 4에서 이미 알고 있던 제약이 이번에
  실제로 발생).
- 화면(목록/상세/차트), README, 이상치 제거 지표는 여전히 안 함.

---

## Day 6 (2026-08-23)

커밋 2개. PROJECT.md 5.2절의 배당 지속성 지표 3개(성장률 둔화·삭감
이력·변동성)를 전부 계산→DB→API까지 완성했다.

### 배당 변동성 (`e5ef8cb`, `b5f613f`)

최근 10년치 연간 배당 증감률(10개)의 **표본** 표준편차(`N-1`로 나눔,
성장률 둔화의 10년 장기 구간과 일관성 맞춰 표본 기간도 10년으로
확정 — 둘 다 사용자 확인). 제곱근은 [[dividend-growth-deceleration]]의
`NthRootCalculator`(뉴턴-랩슨 직접 구현)와 달리, 정수 제곱근은 Java 9+
표준 라이브러리 `BigDecimal.sqrt(MathContext)`가 이미 있어서 그대로
재사용 — 불필요한 재구현을 피함.

- `DividendVolatilityCalculator`(순수) + `DividendVolatilityService`(DB
  연결, 연도별 TTM 지점 11개 조회) + `VolatilityController`
  (`GET /api/tickers/{symbol}/volatility?asOf=`).
- 예측 #16~18(11개 지점 필요한데 10개만 쓸 것, 모집단 공식(N)으로
  잘못 나눌 것, `BigDecimal.sqrt()` 대신 `double`/`NthRootCalculator`
  오용) **전부 틀림**.
- 예측 못 한 문제 하나: 증감률이 전부 동일한 케이스는 이론상 분산이
  정확히 0이어야 하는데, `D_i`를 반복 곱셈으로 만드는 과정에서
  `MathContext.DECIMAL64` 반올림이 16번째 유효숫자에서 흔들려 완전한
  0이 아닌 극소값이 나옴 — `docs/ai-defects/02-mathcontext-precision-drift.md`와
  **같은 현상이 다른 지표에서 재발**. 새 defect 문서를 따로 만들진
  않고 허용오차 비교로 수정(이미 알려진 패턴의 반복이라 판단).
- 실데이터 검증(워크플로우 7단계) 중 Bastion 세션 자체는 서버 쪽에
  살아있었지만 로컬 SSH 프로세스만 끊겨 있던 걸 발견 — 터널 재연결만으로
  복구(세션 재생성까지는 불필요했음, Day 5의 경우보다 가벼운 장애).
  실제 KO 데이터: `meanGrowthRate≈4.35%`, `standardDeviation≈1.19%p` —
  변동성이 낮게 나옴(안정적인 배당주다운 결과).

### 참고 — 아직 안 끝난 것

- PROJECT.md 5.2절 배당 지속성 지표 3개 전부 완성 — 다음은 5.3절
  이상치 제거, 또는 Day 10~12 화면 작업 중 선택 필요.
- OCI Bastion 세션·SSH 터널은 여전히 수동 관리 — 로컬 개발 환경을
  재시작할 때마다 살아있는지 먼저 확인해야 함.
- 화면(목록/상세/차트), README는 여전히 안 함.

## Day 7 (2026-08-23)

커밋 2개(`e773c7f`, `de09163`). PROJECT.md 5.3절 "이상치 제거"의
남은 범위를 처리했다 — 원래 계획했던 "정기 배당 주기를 지급 이력에서
직접 추론하는 알고리즘"은 이미 `docs/decisions/04-dividend-classification.md`로
해소돼 있었다는 걸 이번에 다시 확인했다(Massive의 `dividend_type`/
`frequency` 필드를 그대로 신뢰하기로 결정, `MassiveDividendMapper`가
이미 구현 중). 그래서 이번 작업은 "이미 분류된 데이터 중 뭐가 왜
제외됐는지 보여주는" 순수 조회 기능(특별배당 내역 표시)으로 범위를
좁혀서 진행했다 — 계산 로직이 없는 첫 지표.

### 특별배당 내역 표시 (`e773c7f`, `de09163`)

- `DisclosureEntry`/`SpecialDividendDisclosure`(순수, 계산 없음) +
  `SpecialDividendDisclosureService`(DB 조회) +
  `SpecialDividendDisclosureController`
  (`GET /api/tickers/{symbol}/special-dividends`, `asOf` 파라미터 없음
  — 전체 이력을 한 번에 보여주는 목록형 지표라 기준 시점 개념이 없음).
- 사용자 확인 두 가지: (1) 5.3절 남은 범위를 "특별배당 내역 API만
  추가"로 한정(분류 재구현이나 5.4절로 건너뛰는 대신), (2) "제외
  근거"는 스키마 변경(`rawProviderType` 컬럼 추가) 없이 고정 문구로
  표시.
- 예측 #19("단순 조회"라 착각해서 raw amount 대신
  `TtmDividendAggregationService`의 분할조정 금액을 재활용할 것)
  **틀림** — `DisclosureEntry.from()`이 `TtmDividendAggregationService`를
  아예 참조하지 않고 raw `amount`만 담도록 구현, COST의 실제 $15
  특별배당을 손계산 케이스로 검증.
- 드라이브바이 수정: `Ticker.regularPaymentsPerYear`의 자바독이
  "수동으로 채워 넣는 값"이라고 돼 있었는데 실제로는 이미
  `MassiveDividendIngestionService`가 자동으로 채우고 있어서(결정
  04) 문서-코드 불일치를 발견 — 로직 변경 없이 주석만 정정.
- 실데이터 검증(워크플로우 7단계): KO 94건 전부 REGULAR로 정확히
  조회됨(`specialCount=0`, KO는 실제로 특별배당 이력이 없는 종목이라
  기대한 결과). COST는 이 DB에 실제로 적재돼 있지 않아 SPECIAL 경로는
  실데이터로 재확인은 못 했고, 순수 단위 테스트의 COST 손계산 케이스로
  대체 검증.

### 참고 — 아직 안 끝난 것

- 이걸로 PROJECT.md 5.3절도 사실상 마무리(분류는 결정 04, 표시는
  이번 작업). 다음은 5.4절(시계열 정합성 검증) 또는 Day 10~12 화면
  작업 중 선택 필요 — 아직 미정.
- OCI Bastion 세션·SSH 터널은 여전히 수동 관리. 이번엔 세션 자체는
  살아있어서(Day 6과 동일 패턴) 터널 재연결만으로 복구됨.
- 화면(목록/상세/차트), README는 여전히 안 함.
