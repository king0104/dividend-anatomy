package com.dividendanatomy.domain.math;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * BigDecimal은 정수 거듭제곱(pow(int))만 지원하고 분수 지수 연산이 없어서,
 * n제곱근을 뉴턴-랩슨법으로 직접 구현한다. Math.pow/double은 쓰지 않는다
 * (CLAUDE.md, ArchUnit이 domain 패키지에서 강제).
 */
public final class NthRootCalculator {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-15");
    private static final int MAX_ITERATIONS = 100;

    private NthRootCalculator() {
    }

    public static BigDecimal nthRoot(BigDecimal value, int n, MathContext mc) {
        if (n < 1) {
            throw new IllegalArgumentException("n(%d)은 1 이상이어야 한다".formatted(n));
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("value(%s)는 0 이상이어야 한다".formatted(value));
        }
        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal nBig = BigDecimal.valueOf(n);
        BigDecimal nMinusOne = BigDecimal.valueOf(n - 1L);
        BigDecimal guess = BigDecimal.ONE;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            BigDecimal guessPowNMinusOne = guess.pow(n - 1, mc);
            BigDecimal next = nMinusOne.multiply(guess, mc)
                    .add(value.divide(guessPowNMinusOne, mc), mc)
                    .divide(nBig, mc);
            if (next.subtract(guess, mc).abs().compareTo(TOLERANCE) < 0) {
                return next;
            }
            guess = next;
        }

        throw new ArithmeticException("nthRoot(%s, %d) 수렴 실패".formatted(value, n));
    }
}
