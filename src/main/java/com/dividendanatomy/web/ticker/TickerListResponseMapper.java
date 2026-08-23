package com.dividendanatomy.web.ticker;

import com.dividendanatomy.domain.ticker.DividendIncreaseStreakStatus;
import com.dividendanatomy.domain.ticker.TickerSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

public final class TickerListResponseMapper {

    private TickerListResponseMapper() {
    }

    public static TickerListResponse toResponse(List<TickerSummary> summaries) {
        return new TickerListResponse(summaries.stream().map(TickerListResponseMapper::toEntryResponse).toList());
    }

    private static TickerSummaryResponse toEntryResponse(TickerSummary summary) {
        boolean streakCalculated = summary.streakResult().status() == DividendIncreaseStreakStatus.CALCULATED;
        return new TickerSummaryResponse(
                summary.symbol(),
                summary.name(),
                summary.currency(),
                summary.currentPrice().orElse(null),
                summary.regularPaymentsPerYear(),
                toPercent(summary.yieldResult().currentYield()),
                summary.yieldResult().dataComplete(),
                summary.streakResult().status(),
                streakCalculated ? summary.streakResult().streakYears() : null);
    }

    private static BigDecimal toPercent(Optional<BigDecimal> raw) {
        return raw.map(value -> value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
    }
}
