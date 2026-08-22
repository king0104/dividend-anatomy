package com.dividendanatomy.web.yield;

import java.math.BigDecimal;
import java.time.LocalDate;

public record YieldDecompositionResponse(
        String tickerSymbol,
        LocalDate t0,
        LocalDate t1,
        ContributionDto actual,
        ContributionDto annualized,
        DataQuality dataQuality) {

    public record ContributionDto(BigDecimal priceContributionPercent, BigDecimal dividendContributionPercent) {
    }

    public record DataQuality(
            boolean priceFallbackUsedAtT0,
            boolean priceFallbackUsedAtT1,
            boolean ttmCompleteAtT0,
            boolean ttmCompleteAtT1) {
    }
}
