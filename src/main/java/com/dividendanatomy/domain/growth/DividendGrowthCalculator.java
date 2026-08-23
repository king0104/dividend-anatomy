package com.dividendanatomy.domain.growth;

import com.dividendanatomy.domain.math.NthRootCalculator;
import com.dividendanatomy.domain.yield.TtmDividendSummary;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;

/**
 * TtmDividendSummary 두 시점을 받아 CAGR을 계산하고, 최근 3년 CAGR이
 * 10년 CAGR보다 낮으면 성장 둔화로 판정한다.
 * annualizedSum(≠ actualSum)을 쓰는 이유는
 * docs/specs/dividend-growth-deceleration.md 1.2절 —
 * docs/decisions/05-ttm-window-boundary-fix.md에서 확인한 캘린더
 * 드리프트로 인한 가짜 성장/둔화 신호를 방지한다.
 */
public final class DividendGrowthCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;

    private DividendGrowthCalculator() {
    }

    public static Optional<BigDecimal> cagr(TtmDividendSummary base, TtmDividendSummary earlier, int years) {
        if (!base.isComplete() || !earlier.isComplete()) {
            return Optional.empty();
        }
        BigDecimal earlierAmount = earlier.annualizedSum();
        if (earlierAmount == null || earlierAmount.signum() == 0) {
            return Optional.empty();
        }
        BigDecimal ratio = base.annualizedSum().divide(earlierAmount, MC);
        return Optional.of(NthRootCalculator.nthRoot(ratio, years, MC).subtract(BigDecimal.ONE, MC));
    }

    public static GrowthDecelerationResult evaluate(
            TtmDividendSummary t1, TtmDividendSummary t1Minus3, TtmDividendSummary t1Minus10) {
        Optional<BigDecimal> shortCagr = cagr(t1, t1Minus3, 3);
        Optional<BigDecimal> longCagr = cagr(t1, t1Minus10, 10);

        if (shortCagr.isEmpty() || longCagr.isEmpty()) {
            return new GrowthDecelerationResult(shortCagr, longCagr, GrowthDecelerationStatus.INSUFFICIENT_DATA, Optional.empty());
        }

        boolean decelerating = shortCagr.get().compareTo(longCagr.get()) < 0;
        Optional<BigDecimal> gap = decelerating
                ? Optional.of(longCagr.get().subtract(shortCagr.get(), MC))
                : Optional.empty();

        return new GrowthDecelerationResult(
                shortCagr, longCagr,
                decelerating ? GrowthDecelerationStatus.DECELERATING : GrowthDecelerationStatus.NOT_DECELERATING,
                gap);
    }
}
