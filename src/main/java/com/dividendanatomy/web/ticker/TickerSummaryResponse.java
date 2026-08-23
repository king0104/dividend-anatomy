package com.dividendanatomy.web.ticker;

import com.dividendanatomy.domain.ticker.DividendIncreaseStreakStatus;

import java.math.BigDecimal;

public record TickerSummaryResponse(
        String symbol,
        String name,
        String currency,
        BigDecimal currentPrice,
        Integer regularPaymentsPerYear,
        BigDecimal currentYieldPercent,
        boolean dataComplete,
        DividendIncreaseStreakStatus streakStatus,
        Integer streakYears) {
}
