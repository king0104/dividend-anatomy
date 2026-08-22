# 전체 컨텍스트 테스트가 실제 OCI MySQL(SSH 터널)에 몰래 의존하게 됨

**날짜**: 2026-08-23
**관련 커밋**: (이 문서와 같은 커밋 — 삭감 이력 탐지 지표 구현 중 발견)

## 무슨 일이 있었나

OCI MySQL Always Free DB를 붙이면서 `src/main/resources/application.properties`에
아래를 추가했다:

```properties
spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:13306}/dividend_anatomy?useSSL=false&serverTimezone=UTC
spring.datasource.username=${OCI_MYSQL_ADMIN_USERNAME:dividend_admin}
spring.datasource.password=${OCI_MYSQL_ADMIN_PASSWORD:}
```

이후 삭감 이력 탐지 지표를 구현하고 `./gradlew test`(전체 테스트)를
돌렸더니 새로 만든 테스트들은 다 통과했는데
`DividendAnatomyApplicationTests.contextLoads()`가 실패했다:

```
Caused by: org.hibernate.service.spi.ServiceException at AbstractServiceRegistryImpl.java:273
    Caused by: org.hibernate.HibernateException at DialectFactoryImpl.java:190
```

OCI 인증 정보(`OCI_MYSQL_ADMIN_PASSWORD` 등)를 셸에 안 실어준 상태로
돌렸더니 난 에러였다. 즉 **일반 `./gradlew test`가 조용히 실제 클라우드
DB(SSH 터널 뒤에 있는 OCI MySQL)에 의존하고 있었다.**

## 왜 놓칠 뻔했나

이 세션 안에서 `TwelveDataPriceIngestionService` 등 다른 서비스들을
`@DataJpaTest`로 검증할 때는 문제가 전혀 없었다 — `@DataJpaTest`는
`@AutoConfigureTestDatabase`가 datasource를 자동으로 embedded H2로
바꿔치기하기 때문에, `application.properties`에 뭘 적어놨든 상관이
없었다. 그래서 "테스트는 어차피 H2를 쓴다"고 착각한 채로 main
`application.properties`에 실제 DB 접속 정보를 그대로 적어 넣었다.
`DividendAnatomyApplicationTests`는 `@SpringBootTest`(전체 컨텍스트)라서
이 자동 치환이 적용되지 않는다는 걸 놓쳤다.

## 어떻게 잡았나

지표 하나(삭감 이력 탐지)를 구현하고 나서 관례대로 "새 테스트만"이
아니라 `./gradlew test`(전체)를 돌려 회귀를 확인하는 과정에서 실패를
발견했다. `--tests` 필터로 새 테스트만 돌렸을 때는 이 문제가 안
보였다 — 전체 스위트를 돌리는 습관이 없었으면 이 세션 안에서는 못
잡고 다음 사람(또는 CI)이 터널 없이 돌렸을 때 처음 겪었을 것이다.

## 어떻게 고쳤나

1. 처음 시도: `src/test/resources/application.properties`(H2 설정)를
   새로 만듦 — 실패. Spring Boot는 클래스패스에서 같은 이름의
   `application.properties`를 하나만 로드하고(프로파일 미지정 파일은
   병합이 아니라 대체), 테스트 리소스가 메인 리소스보다 classpath
   우선순위가 높아서 main의 `application.properties` 전체가 안
   보이게 됨 — `massive.base-url` 등 다른 설정까지 다 사라져서
   `PlaceholderResolutionException`이 새로 남.
2. 최종 수정: 파일을 **프로파일 전용** `src/test/resources/application-test.properties`로
   바꾸고, `DividendAnatomyApplicationTests`에 `@ActiveProfiles("test")`를
   추가. 프로파일별 프로퍼티 파일은 기본 파일 위에 **병합**되므로
   (대체가 아님), main의 다른 설정은 그대로 살아있고 datasource만
   H2로 덮어써진다.

```java
@SpringBootTest
@ActiveProfiles("test")
class DividendAnatomyApplicationTests { ... }
```

```properties
# src/test/resources/application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
```

## 교훈

- `application.properties`에 실제(로컬이 아닌) 인프라 접속 정보를
  넣는 순간, `@DataJpaTest`처럼 datasource를 자동으로 치환해주는
  테스트 슬라이스가 아닌 **전체 컨텍스트 테스트가 있는지부터 확인**해야
  한다.
- 테스트용 프로퍼티 파일을 새로 만들 때 이름을 `application.properties`로
  하면 "덮어쓰기"가 아니라 "완전 대체"라는 걸 기억한다 — 프로파일별
  파일(`application-{profile}.properties`)을 쓰고 그 프로파일을
  명시적으로 활성화해야 "병합"이 된다.
- `--tests`로 새로 만든 테스트만 필터링해서 돌리면 이런 회귀를 놓친다 —
  지표 하나를 완성할 때마다 `./gradlew test`(필터 없이 전체)를 최소
  한 번은 돌리는 습관이 실제로 이번에 회귀를 잡아냈다.
