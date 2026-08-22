# 05. `RestClient.Builder` 빈이 없음 — `spring-boot-starter-restclient`가 별도 스타터로 분리됨

**날짜**: 2026-08-23

## 무슨 일이 있었나

Massive/Twelve Data를 호출할 `TwelveDataClient`, `MassiveClient`를
`RestClient.Builder`를 생성자로 주입받게 만들었다. `spring-boot-starter
-webmvc`가 이미 있으니 `RestClient` 자동구성도 딸려올 거라고 가정하고
별도 의존성을 안 추가했다. 전체 스프링 컨텍스트를 띄우는
`DividendAnatomyApplicationTests.contextLoads()`가 실패했다:

```
NoSuchBeanDefinitionException: No qualifying bean of type
'org.springframework.web.client.RestClient$Builder' available
```

## 왜 놓칠 뻔했나

Boot 3.x에서는 `spring-web`이 classpath에 있으면(예: `spring-boot-
starter-web` 하나로 웹 서버 + 클라이언트 유틸리티가 다 딸려왔음)
`RestClientAutoConfiguration`이 자동으로 활성화됐다. Boot 4.1은 이미
CLAUDE.md에 "스타터가 기술별로 쪼개졌다"고 경고해뒀는데도, "서버(webmvc)
스타터가 있으니 클라이언트도 되겠지"라고 다시 한번 3.x 시절 감각으로
넘겨짚었다 — `spring-boot-autoconfigure-4.1.1.jar`를 직접 뒤져봐도
`RestClientAutoConfiguration` 클래스 자체가 없었다(별도 모듈로 완전히
빠져나감).

## 어떻게 잡았나

`./gradlew test`로 전체 스위트를 돌리다가 무관해 보이는
`DividendAnatomyApplicationTests`(전체 컨텍스트 로딩 스모크 테스트)가
깨진 걸 보고, 스택 트레이스에서 `RestClient$Builder` 빈이 없다는 걸
확인했다. `spring-boot-autoconfigure-4.1.1.jar`를 `unzip -l`로 뒤져
`RestClientAutoConfiguration`이 아예 없다는 걸 확인하고, Boot 4.1의
`-data-jpa`/`-webmvc`처럼 별도 스타터가 있을 거라 추측해
`spring-boot-starter-restclient`를 `build.gradle`에 추가 → `./gradlew
dependencies`로 실제 해석되는지 확인 → 성공.

## 어떻게 고쳤나

```gradle
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-webmvc'
implementation 'org.springframework.boot:spring-boot-starter-restclient'  // 추가
```

## 교훈

- `docs/ai-defects/03`(`@DataJpaTest` 패키지 이동)에 이어 **같은
  근본 원인(Boot 4의 기능별 스타터 분리)이 세 번째로 실제 문제를
  일으켰다** (`ai-predictions.md` #6). 서버 스타터가 있다고 클라이언트
  기능까지 딸려온다고 가정하면 안 된다 — Boot 4.1에서는 "이 기능을 쓰려면
  어떤 스타터가 필요한지"를 매번 각각 확인해야 한다.
- 짐작으로 이름을 붙여보고(`spring-boot-starter-restclient`) `./gradlew
  dependencies`로 실제 해석되는지 확인하는 방식이 이번에도 통했다 —
  Boot 4 스타터 이름이 대체로 "기능명 그대로"라 짐작이 잘 맞는 편이지만,
  짐작 자체를 검증 없이 믿지 않고 반드시 실행해서 확인해야 한다.
