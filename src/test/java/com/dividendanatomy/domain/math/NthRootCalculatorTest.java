package com.dividendanatomy.domain.math;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NthRootCalculatorTest {

    private static final MathContext MC = MathContext.DECIMAL64;
    /** 순환소수가 아닌데도 뉴턴-랩슨 반복 경로상 마지막 몇 자리가 흔들릴 수 있어 허용오차로 비교한다. */
    private static final BigDecimal TOLERANCE = new BigDecimal("1E-10");

    @Test
    void cubeRootOfEightIsTwo() {
        BigDecimal result = NthRootCalculator.nthRoot(new BigDecimal("8"), 3, MC);
        assertBigDecimalApprox(new BigDecimal("2"), result);
    }

    /** 1.1^3 = 1.331 (정확) — 손계산으로 검증한 케이스. */
    @Test
    void cubeRootOf1331IsOnePointOne() {
        BigDecimal result = NthRootCalculator.nthRoot(new BigDecimal("1.331"), 3, MC);
        assertBigDecimalApprox(new BigDecimal("1.1"), result);
    }

    /** 1.1^10 = 2.5937424601 (정확) — 손계산으로 검증한 케이스. */
    @Test
    void tenthRootOf2p5937424601IsOnePointOne() {
        BigDecimal result = NthRootCalculator.nthRoot(new BigDecimal("2.5937424601"), 10, MC);
        assertBigDecimalApprox(new BigDecimal("1.1"), result);
    }

    @Test
    void rootOfZeroIsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(NthRootCalculator.nthRoot(BigDecimal.ZERO, 4, MC)));
    }

    @Test
    void rootOfOneIsOne() {
        assertBigDecimalApprox(BigDecimal.ONE, NthRootCalculator.nthRoot(BigDecimal.ONE, 5, MC));
    }

    @Test
    void firstRootIsIdentity() {
        BigDecimal value = new BigDecimal("3.14159");
        assertBigDecimalApprox(value, NthRootCalculator.nthRoot(value, 1, MC));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> NthRootCalculator.nthRoot(BigDecimal.ONE, 0, MC));
        assertThrows(IllegalArgumentException.class, () -> NthRootCalculator.nthRoot(new BigDecimal("-1"), 2, MC));
    }

    private static void assertBigDecimalApprox(BigDecimal expected, BigDecimal actual) {
        BigDecimal diff = expected.subtract(actual, MC).abs();
        assertTrue(diff.compareTo(TOLERANCE) < 0,
                () -> "expected=%s actual=%s diff=%s".formatted(expected, actual, diff));
    }
}
