package com.dividendanatomy.domain.ticker;

import com.dividendanatomy.domain.yield.TtmDividendSummary;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;

public record CurrentYieldResult(Optional<BigDecimal> currentYield, boolean dataComplete) {

    private static final MathContext MC = MathContext.DECIMAL64;

    public static CurrentYieldResult from(TtmDividendSummary ttm, Optional<BigDecimal> price) {
        if (ttm.annualizedSum() == null || price.isEmpty()) {
            return new CurrentYieldResult(Optional.empty(), ttm.isComplete());
        }
        BigDecimal yield = ttm.annualizedSum().divide(price.get(), MC);
        return new CurrentYieldResult(Optional.of(yield), ttm.isComplete());
    }
}
