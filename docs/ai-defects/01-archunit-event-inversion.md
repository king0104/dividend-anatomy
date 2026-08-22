# 01. ArchUnit `noCodeUnits().should(커스텀 조건)`의 이벤트 반전을 몰라서 위반이 통과함

**날짜**: 2026-08-23
**관련 커밋**: `14eac39`

## 무슨 일이 있었나

domain 패키지에서 메서드 파라미터에 `double`/`float`를 못 쓰게 막는 ArchUnit
규칙을 짜면서, 위반 시 아래처럼 `events.add(SimpleConditionEvent.violated(...))`를
직접 호출했다.

```java
if (hasBannedParameter) {
    events.add(SimpleConditionEvent.violated(codeUnit,
            codeUnit.getFullName() + " has a double/float parameter"));
}
```

컴파일도 되고, 필드/반환타입 규칙(내장 조건 `haveRawType`, `haveRawReturnType`
사용)은 정상 작동했다. 그런데 파라미터 규칙만 실제로 `double` 파라미터가 있는
메서드를 만들어 테스트해봐도 **조용히 통과**했다 — 빌드가 깨져야 하는데
안 깨졌다.

## 왜 놓칠 뻔했나

`noCodeUnits().should(condition)`은 내부적으로 `NeverCondition`이라는
래퍼로 조건을 감싸서, 조건이 리포트하는 `satisfied`/`violated` 이벤트를
**반전시켜서** 평가한다. 즉 "noX() = X 조건을 만족하는 게 하나도 없어야
한다"는 걸, "조건을 만족함(satisfied)"으로 보고된 케이스를 최종적으로
위반으로 뒤집는 방식으로 구현한다.

내장 조건(`haveRawReturnType(double.class)`)은 "실제로 이 타입이면
satisfied, 아니면 violated"로 보고하도록 이미 그렇게 짜여 있어서
`noCodeUnits()`와 결합하면 자연스럽게 올바르게 동작한다. 그런데 내가 만든
커스텀 조건은 반대로 "배당된(금지된) 파라미터가 있으면 violated"라고
직접 보고해버렸다 — 이러면 `noCodeUnits()`가 한 번 더 반전시켜서
"위반이 있었다"가 "위반 없음"으로 다시 뒤집혀 버린다. 이중 반전.

증상만 보면 "규칙이 그냥 안 걸리네" 정도라 못 보고 넘어가기 쉬웠다.

## 어떻게 잡았나

CLAUDE.md 규칙("테스트 없는 계산 로직 추가 금지")과 같은 정신으로, ArchUnit
규칙 자체도 "규칙이 진짜로 위반을 잡아내는지" 직접 위반 클래스를 만들어
검증했다:

```java
class BadMoney {
    void setRate(double rate) {}
}
```

이 클래스를 domain 패키지에 임시로 넣고 테스트를 돌렸는데 빌드가
성공해버려서 문제를 발견했다. `System.err.println`으로 조건의 `check()`가
호출되는지, `events` 객체의 실제 런타임 타입이 뭔지 찍어보니
`NeverCondition$InvertingConditionEvents`였다 — 여기서 반전 구조를 알아챘다.

## 어떻게 고쳤나

`violated`를 직접 넣는 대신, "조건이 실제로 참인가"를 `satisfied` 값으로
그대로 보고하도록 바꿨다:

```java
events.add(new SimpleConditionEvent(codeUnit, hasBannedParameter, message));
```

`hasBannedParameter`가 `true`(배당된 파라미터가 있음 = 조건 만족)면
`noCodeUnits()`가 이를 반전시켜 올바르게 위반으로 보고한다.

## 교훈

- ArchUnit의 `noX().should(커스텀 조건)`을 쓸 때는 조건을 "무엇이 있으면
  안 되는가"가 아니라 "무엇이 있으면 satisfied인가"의 관점에서 작성해야
  한다 — `noX()`가 알아서 뒤집는다.
- **검사 규칙 자체도 검증 없이 믿으면 안 된다.** 컴파일되고 빌드가 통과한다고
  규칙이 실제로 일하고 있다는 뜻이 아니다 — 일부러 위반 케이스를 만들어
  빌드가 깨지는지 직접 확인해야 신뢰할 수 있다.
