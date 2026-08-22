# 03. `@DataJpaTest`가 Spring Boot 4.1에서 다른 패키지로 옮겨감

**날짜**: 2026-08-23

## 무슨 일이 있었나

리포지토리 테스트를 짜면서 `@DataJpaTest`를 Boot 3.x 시절 그대로

```java
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
```

로 import했다. 저장 직후 IDE 진단이 바로 에러를 띄웠다:

```
"DataJpaTest cannot be resolved to a type" (code 16777218)
"The import org.springframework.boot.test.autoconfigure.orm cannot be resolved" (code 268435846)
```

## 왜 놓칠 뻔했나

`build.gradle`에 `spring-boot-starter-data-jpa-test`가 이미 있고
`./gradlew dependencies`로 확인해도 `spring-boot-test-autoconfigure`가
테스트 classpath에 정상적으로 걸려 있어서, "의존성은 맞게 넣었으니 import
경로도 옛날 그대로겠지"라고 넘겨짚기 쉬웠다. 실제로는 Boot 4에서 스타터가
기능별로 쪼개진 것처럼(`CLAUDE.md`에 이미 경고해둔 대로) `@DataJpaTest`
자체도 `spring-boot-test-autoconfigure`가 아니라 별도
`spring-boot-data-jpa-test` 모듈로 옮겨갔고, 패키지도
`org.springframework.boot.test.autoconfigure.orm.jpa` →
`org.springframework.boot.data.jpa.test.autoconfigure`로 바뀌었다.

## 어떻게 잡았나

파일을 쓰자마자 IDE 진단(PostToolUse 훅이 아니라 에디터 자체의 실시간
진단)이 바로 에러를 띄워서 컴파일까지 갈 필요도 없이 알아챘다. 실제
패키지 위치는 `./gradlew dependencies`로 걸린 jar들을 하나씩
`unzip -l`로 뒤져서 `DataJpaTest.class`가 들어있는 jar
(`spring-boot-data-jpa-test-4.1.1.jar`)를 찾고, 그 안의 실제 클래스
경로(`org/springframework/boot/data/jpa/test/autoconfigure/DataJpaTest.class`)
를 확인해서 알아냈다.

## 어떻게 고쳤나

```java
// Before (Boot 3.x)
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
// After (Boot 4.1)
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
```

수정 후 `compileTestJava`, 이어서 실제 `@DataJpaTest` 테스트 실행까지
확인해서 임베디드 H2가 정상적으로 붙는 것까지 검증했다.

## 교훈

- `docs/ai-predictions.md` #6("Spring Boot 3.x 시절 API를 그대로 생성할
  것")이 실제로 적중한 첫 사례. 스타터 이름뿐 아니라 **테스트
  어노테이션의 패키지 경로**도 모듈이 쪼개지면서 바뀔 수 있다는 걸
  구체적으로 확인했다 — CLAUDE.md의 경고가 추상적인 우려가 아니라 실제로
  마주치는 문제라는 증거.
- 의존성(jar)이 classpath에 있다는 것과 "내가 쓰려는 클래스가 내가 아는
  패키지 경로에 있다"는 것은 별개다. 클래스가 안 잡히면 라이브러리
  자체를 의심하기 전에, **같은 라이브러리의 최신 문서/실제 jar 내용을
  먼저 확인**한다 (`unzip -l`로 jar 안을 직접 뒤지는 방법이 실제로
  통했다).
- IDE 실시간 진단이 컴파일보다 먼저 이런 걸 잡아준다 — Write 직후
  나오는 진단 메시지를 무시하지 않고 바로 다음 스텝에서 확인하는 습관이
  이번에 유효했다.
