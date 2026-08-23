package com.dividendanatomy.domain.volatility;

import com.dividendanatomy.domain.yield.TtmDividendSummary;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 연간 배당 증감률의 표본 표준편차(N-1로 나눔)를 구한다.
 * 제곱근은 BigDecimal.sqrt(MathContext)(Java 9+ 표준 라이브러리)를
 * 그대로 쓴다 — NthRootCalculator처럼 직접 구현하지 않는다
 * (docs/specs/dividend-volatility.md 0절).
 */
public final class DividendVolatilityCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int SAMPLE_YEARS = 10;

    private DividendVolatilityCalculator() {
    }

    /** orderedSummaries는 오래된 것 → 최신 순, 정확히 SAMPLE_YEARS+1(11)개여야 한다. */
    public static VolatilityResult evaluate(List<TtmDividendSummary> orderedSummaries) {
        if (orderedSummaries.size() != SAMPLE_YEARS + 1
                || orderedSummaries.stream().anyMatch(s -> !s.isComplete())) {
            return new VolatilityResult(VolatilityStatus.INSUFFICIENT_DATA, Optional.empty(), Optional.empty());
        }

        List<BigDecimal> growthRates = new ArrayList<>();
        for (int i = 1; i < orderedSummaries.size(); i++) {
            BigDecimal prev = orderedSummaries.get(i - 1).annualizedSum();
            BigDecimal curr = orderedSummaries.get(i).annualizedSum();
            if (prev.signum() == 0) {
                return new VolatilityResult(VolatilityStatus.INSUFFICIENT_DATA, Optional.empty(), Optional.empty());
            }
            growthRates.add(curr.subtract(prev, MC).divide(prev, MC));
        }

        BigDecimal mean = sum(growthRates).divide(BigDecimal.valueOf(growthRates.size()), MC);

        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (BigDecimal g : growthRates) {
            BigDecimal diff = g.subtract(mean, MC);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff, MC), MC);
        }
        BigDecimal variance = sumSquaredDiff.divide(BigDecimal.valueOf(growthRates.size() - 1L), MC);
        BigDecimal stdDev = variance.sqrt(MC);

        return new VolatilityResult(VolatilityStatus.COMPLETE, Optional.of(mean), Optional.of(stdDev));
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value, MC);
        }
        return total;
    }
}
