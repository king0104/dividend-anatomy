package com.dividendanatomy.web.volatility;

import com.dividendanatomy.domain.volatility.VolatilityResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * %p 반올림(scale=2, HALF_UP)을 응답 매핑 시점에만 적용한다
 * (docs/specs/dividend-volatility.md 3절).
 */
public final class VolatilityResponseMapper {

    private VolatilityResponseMapper() {
    }

    public static VolatilityResponse toResponse(String symbol, LocalDate asOf, VolatilityResult result) {
        return new VolatilityResponse(
                symbol,
                asOf,
                result.status(),
                toPercent(result.meanGrowthRate()),
                toPercent(result.standardDeviation()));
    }

    private static BigDecimal toPercent(Optional<BigDecimal> raw) {
        return raw.map(value -> value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
    }
}
