package com.dividendanatomy.domain.volatility;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * status == INSUFFICIENT_DATA면 두 값 다 empty.
 */
public record VolatilityResult(
        VolatilityStatus status,
        Optional<BigDecimal> meanGrowthRate,
        Optional<BigDecimal> standardDeviation) {
}
