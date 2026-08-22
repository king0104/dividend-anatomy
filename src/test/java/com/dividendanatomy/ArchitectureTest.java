package com.dividendanatomy;

import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * CLAUDE.md: "금액·비율 계산에는 BigDecimal만 쓴다. double/float 금지."
 * 돈 계산은 환율까지 곱해져 부동소수점 오차가 실제로 드러나므로,
 * 도메인 패키지에서는 double/float를 컴파일 시점이 아니라 테스트 시점에
 * 잡아 강제한다.
 */
@AnalyzeClasses(packages = "com.dividendanatomy")
class ArchitectureTest {

    private static final String REASON = "돈 계산은 BigDecimal만 사용한다 (CLAUDE.md)";

    // allowEmptyShould(true): 아직 domain 패키지에 클래스가 없어도(이 규칙이
    // 검사할 대상이 0개여도) 실패시키지 않는다. 나중에 domain 클래스가 생기는
    // 순간부터 이 규칙이 실제로 작동한다.

    @ArchTest
    static final ArchRule domain_fields_must_not_be_double_or_float =
            noFields()
                    .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
                    .should().haveRawType(double.class)
                    .orShould().haveRawType(float.class)
                    .because(REASON)
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_methods_must_not_return_double_or_float =
            noCodeUnits()
                    .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
                    .should().haveRawReturnType(double.class)
                    .orShould().haveRawReturnType(float.class)
                    .because(REASON)
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_methods_must_not_have_double_or_float_parameters =
            noCodeUnits()
                    .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
                    .should(new ArchCondition<JavaCodeUnit>("have a double or float parameter") {
                        @Override
                        public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                            boolean hasBannedParameter = codeUnit.getRawParameterTypes().stream()
                                    .anyMatch(type -> type.isEquivalentTo(double.class)
                                            || type.isEquivalentTo(float.class));
                            String message = hasBannedParameter
                                    ? codeUnit.getFullName() + " has a double/float parameter"
                                    : codeUnit.getFullName() + " has no double/float parameter";
                            // noCodeUnits()는 내부적으로 이 이벤트를 반전시켜 평가하므로,
                            // "조건이 실제로 참(=배당된 파라미터가 있음)"일 때 satisfied=true로
                            // 보고해야 noCodeUnits() 하에서 올바르게 위반으로 뒤집힌다.
                            events.add(new SimpleConditionEvent(codeUnit, hasBannedParameter, message));
                        }
                    })
                    .because(REASON)
                    .allowEmptyShould(true);
}
