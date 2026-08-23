package com.dividendanatomy.domain.split;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SplitAdjustmentCalculatorTest {

    private static final Ticker TICKER = new Ticker("KO", "Coca-Cola", "USD");

    @Test
    void returnsRawAmountWhenNoLaterSplits() {
        BigDecimal result = SplitAdjustmentCalculator.adjustedAmount(List.of(), new BigDecimal("1.53"));

        assertEquals(0, new BigDecimal("1.53").compareTo(result));
    }

    @Test
    void dividesByCumulativeRatioForSingleSplit() {
        SplitEvent split = new SplitEvent(TICKER, LocalDate.parse("2012-08-13"), new BigDecimal("2"), DataSource.MASSIVE);

        BigDecimal result = SplitAdjustmentCalculator.adjustedAmount(List.of(split), new BigDecimal("1.02"));

        assertEquals(0, new BigDecimal("0.51").compareTo(result));
    }

    @Test
    void multipliesRatiosForMultipleLaterSplits() {
        SplitEvent split1 = new SplitEvent(TICKER, LocalDate.parse("2010-01-01"), new BigDecimal("2"), DataSource.MASSIVE);
        SplitEvent split2 = new SplitEvent(TICKER, LocalDate.parse("2015-01-01"), new BigDecimal("3"), DataSource.MASSIVE);

        BigDecimal result = SplitAdjustmentCalculator.adjustedAmount(List.of(split1, split2), new BigDecimal("6.00"));

        assertEquals(0, new BigDecimal("1.00").compareTo(result));
    }
}
