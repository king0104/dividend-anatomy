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

## Day 2 (예정)

- 슬래시 커맨드 `/spec`, `/defect`, `/verify`
- 배당 규칙 조사 — T+1 전환 SEC 원문 인용
- `docs/ai-predictions.md`는 이미 있음 → 결과 칸 채우기는 구현 진행하며
- `docs/workflow.md` — 지표 추가 8단계
- 지표별 스펙 확정(`docs/specs/`) → 테스트 먼저 작성
