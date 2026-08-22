# DB 선택

PROJECT.md 8장이 "DB (미정 — Day 1 결정)"으로 명시한 항목.

## 후보와 비교

| | PostgreSQL | MySQL | SQLite |
|---|---|---|---|
| BigDecimal/NUMERIC 정밀도 | 좋음 | 좋음(8.x 기준) | 좋음(단, 타입 강제 약함) |
| Spring Data JPA 지원 | 1급 | 1급 | 공식 dialect 없음, 커뮤니티 dialect 필요 |
| 이 프로젝트 규모(57종목, 일 1회 배치)에 적합한가 | 과함(과해도 무해) | 과함(과해도 무해) | 충분함 |
| 리스크 | 없음 | 없음 | 2주 일정 안에 예기치 않은 호환성 문제 가능 |

세 후보 다 기술적으로는 이 프로젝트를 감당할 수 있다. SQLite는 Hibernate
공식 지원이 아니라는 게 2주짜리 일정에서 불필요한 리스크라고 판단해 먼저
제외했다.

## PostgreSQL vs MySQL — 결정

기술적으로는 거의 동급이라 처음엔 AI가 PostgreSQL을 제안했다 (근거: Spring
Boot 4.1 관련 최신 예제가 Postgres를 기본값으로 쓰는 경우가 많아 AI 코드
생성 리스크를 줄임, JSONB의 JPA 생태계 성숙도, 거버넌스).

그런데 사용자가 **MySQL에 이미 익숙하다**는 이유로 MySQL을 선택했다. 이건
AI가 처음에 고려하지 못했던 조건이자, 이 프로젝트 규모·일정(2주)에서는
AI가 댄 소프트한 근거들(예제 편향, JSONB 성숙도, 거버넌스)보다 훨씬 강한
근거다 — 익숙한 도구를 쓰면 그만큼 계산 엔진(이 프로젝트의 진짜 핵심)에
시간을 더 쓸 수 있다.

→ **최종 결정: MySQL.** 기술적으로 이 프로젝트를 감당하는 데 문제없고
(BigDecimal ↔ `DECIMAL` 매핑, Spring Data JPA 1급 지원 모두 동일), 사용자
숙련도가 AI의 일반론적 근거를 뒤집을 만큼 실질적인 이유였다.

## 적용

- 운영/개발: MySQL (`com.mysql:mysql-connector-j`)
- 테스트: H2 (인메모리, Gradle 뼈대에 `testRuntimeOnly`로 포함됨) — 빠른
  테스트 실행을 위해 유지. 실제 DB와 SQL 방언 차이로 테스트가 놓치는 게
  생기면 Testcontainers(MySQL 컨테이너)로 전환 검토
