package com.dividendanatomy.domain.ticker;

import com.dividendanatomy.domain.yield.TtmDividendSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentYieldResultTest {

    @Test
    void computesYieldFromAnnualizedSumAndPrice() {
        // annualizedSum=2.00, price=40.00 -> yield=0.05 (5%)
        TtmDividendSummary ttm = new TtmDividendSummary(new BigDecimal("2.00"), new BigDecimal("2.00"), 4, 4);

        CurrentYieldResult result = CurrentYieldResult.from(ttm, Optional.of(new BigDecimal("40.00")));

        assertTrue(result.currentYield().isPresent());
        assertEquals(0, new BigDecimal("0.05").compareTo(result.currentYield().get()));
        assertTrue(result.dataComplete());
    }

    @Test
    void returnsEmptyWhenPriceMissing() {
        TtmDividendSummary ttm = new TtmDividendSummary(new BigDecimal("2.00"), new BigDecimal("2.00"), 4, 4);

        CurrentYieldResult result = CurrentYieldResult.from(ttm, Optional.empty());

        assertFalse(result.currentYield().isPresent());
    }

    @Test
    void returnsEmptyWhenAnnualizedSumIsNull() {
        TtmDividendSummary ttm = new TtmDividendSummary(BigDecimal.ZERO, null, 0, 4);

        CurrentYieldResult result = CurrentYieldResult.from(ttm, Optional.of(new BigDecimal("40.00")));

        assertFalse(result.currentYield().isPresent());
        assertFalse(result.dataComplete());
    }

    @Test
    void marksDataIncompleteWhenTtmWindowIsIncompleteEvenThoughYieldIsComputed() {
        TtmDividendSummary ttm = new TtmDividendSummary(new BigDecimal("1.50"), new BigDecimal("2.00"), 3, 4);

        CurrentYieldResult result = CurrentYieldResult.from(ttm, Optional.of(new BigDecimal("40.00")));

        assertTrue(result.currentYield().isPresent());
        assertFalse(result.dataComplete());
    }
}
