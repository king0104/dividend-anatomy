package com.dividendanatomy.domain.yield;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;

/**
 * 배당수익률 변화(ΔY = D1/P1 - D0/P0)를 가격 기여도와 배당 기여도로
 * 분해한다. "가격이 먼저 바뀌었다고 가정" / "배당이 먼저 바뀌었다고
 * 가정" 두 순서의 평균(대칭법)을 쓴다 — 순차 분해는 어느 쪽을 먼저
 * 고정할지에 대한 근거 없는 임의성이 생기기 때문에 쓰지 않는다.
 *
 * 닫힌 형태(docs/specs/yield-change-decomposition.md 1.4):
 *   priceContribution    = (D0 + D1) / 2 * (1/P1 - 1/P0)
 *   dividendContribution = (D1 - D0) / 2 * (1/P0 + 1/P1)
 */
public final class YieldChangeDecomposer {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private YieldChangeDecomposer() {
    }

    public static YieldContribution decompose(BigDecimal d0, BigDecimal p0, BigDecimal d1, BigDecimal p1) {
        requirePositive(p0, "p0");
        requirePositive(p1, "p1");

        BigDecimal invP0 = BigDecimal.ONE.divide(p0, MC);
        BigDecimal invP1 = BigDecimal.ONE.divide(p1, MC);

        BigDecimal priceContribution = d0.add(d1)
                .divide(TWO, MC)
                .multiply(invP1.subtract(invP0), MC);

        BigDecimal dividendContribution = d1.subtract(d0)
                .divide(TWO, MC)
                .multiply(invP0.add(invP1), MC);

        return new YieldContribution(priceContribution, dividendContribution);
    }

    public static Optional<YieldContribution> decomposeAnnualized(
            TtmDividendSummary t0, TtmDividendSummary t1, BigDecimal p0, BigDecimal p1) {
        if (t0.foundCount() == 0 || t1.foundCount() == 0) {
            return Optional.empty();
        }
        return Optional.of(decompose(t0.annualizedSum(), p0, t1.annualizedSum(), p1));
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("%s(%s)는 0보다 커야 한다".formatted(name, value));
        }
    }
}
