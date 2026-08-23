package com.dividendanatomy.web.volatility;

import com.dividendanatomy.domain.volatility.VolatilityStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * meanGrowthRatePercent/standardDeviationPercent는 status ==
 * INSUFFICIENT_DATA면 둘 다 null.
 */
public record VolatilityResponse(
        String tickerSymbol,
        LocalDate asOf,
        VolatilityStatus status,
        BigDecimal meanGrowthRatePercent,
        BigDecimal standardDeviationPercent) {
}
