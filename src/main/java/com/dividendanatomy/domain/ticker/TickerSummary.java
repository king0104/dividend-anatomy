package com.dividendanatomy.domain.ticker;

import java.math.BigDecimal;
import java.util.Optional;

public record TickerSummary(
        String symbol,
        String name,
        String currency,
        Optional<BigDecimal> currentPrice,
        Integer regularPaymentsPerYear,
        CurrentYieldResult yieldResult,
        DividendIncreaseStreakResult streakResult) {
}
