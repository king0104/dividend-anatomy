package com.dividendanatomy.web.ticker;

import com.dividendanatomy.domain.ticker.CurrentYieldResult;
import com.dividendanatomy.domain.ticker.DividendIncreaseStreakResult;
import com.dividendanatomy.domain.ticker.DividendIncreaseStreakStatus;
import com.dividendanatomy.domain.ticker.TickerSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickerListResponseMapperTest {

    @Test
    void mapsCalculatedSummary() {
        TickerSummary summary = new TickerSummary(
                "KO", "Coca-Cola", "USD", Optional.of(new BigDecimal("65.00")), 4,
                new CurrentYieldResult(Optional.of(new BigDecimal("0.0435")), true),
                new DividendIncreaseStreakResult(DividendIncreaseStreakStatus.CALCULATED, 62));

        TickerListResponse response = TickerListResponseMapper.toResponse(List.of(summary));

        TickerSummaryResponse entry = response.tickers().get(0);
        assertEquals("KO", entry.symbol());
        assertEquals(0, new BigDecimal("4.35").compareTo(entry.currentYieldPercent()));
        assertEquals(62, entry.streakYears());
        assertTrue(entry.dataComplete());
    }

    @Test
    void nullsOutStreakYearsWhenInsufficientData() {
        TickerSummary summary = new TickerSummary(
                "YOUNG", "Young Co", "USD", Optional.empty(), 4,
                new CurrentYieldResult(Optional.empty(), false),
                new DividendIncreaseStreakResult(DividendIncreaseStreakStatus.INSUFFICIENT_DATA, 0));

        TickerListResponse response = TickerListResponseMapper.toResponse(List.of(summary));

        TickerSummaryResponse entry = response.tickers().get(0);
        assertNull(entry.streakYears());
        assertNull(entry.currentYieldPercent());
        assertNull(entry.currentPrice());
    }
}
