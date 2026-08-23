package com.dividendanatomy.domain.growth;

import com.dividendanatomy.domain.yield.TtmDividendSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DividendGrowthCalculatorTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-10");

    /** 손계산: base=1.331, earlier=1.00, 3년 → (1.331)^(1/3)-1 = 0.10 (10%). */
    @Test
    void cagrMatchesHandCalculatedValue() {
        TtmDividendSummary base = complete("1.331");
        TtmDividendSummary earlier = complete("1.00");

        Optional<BigDecimal> result = DividendGrowthCalculator.cagr(base, earlier, 3);

        assertTrue(result.isPresent());
        assertApprox(new BigDecimal("0.10"), result.get());
    }

    @Test
    void cagrIsEmptyWhenEarlierWindowIsIncomplete() {
        TtmDividendSummary base = complete("1.331");
        TtmDividendSummary earlier = new TtmDividendSummary(new BigDecimal("0.75"), new BigDecimal("1.00"), 3, 4);

        assertEquals(Optional.empty(), DividendGrowthCalculator.cagr(base, earlier, 3));
    }

    @Test
    void cagrIsEmptyWhenBaseWindowIsIncomplete() {
        TtmDividendSummary base = new TtmDividendSummary(new BigDecimal("1.00"), new BigDecimal("1.33"), 3, 4);
        TtmDividendSummary earlier = complete("1.00");

        assertEquals(Optional.empty(), DividendGrowthCalculator.cagr(base, earlier, 3));
    }

    /** 최근 3년은 배당이 그대로(short CAGR=0)인데, 10년 전엔 오히려 더 높았다면(long CAGR<0) 둔화가 아니다. */
    @Test
    void evaluateReturnsNotDeceleratingWhenShortCagrIsNotLower() {
        TtmDividendSummary t1 = complete("1.00");
        TtmDividendSummary t1Minus3 = complete("1.00");
        TtmDividendSummary t1Minus10 = complete("2.00");

        GrowthDecelerationResult result = DividendGrowthCalculator.evaluate(t1, t1Minus3, t1Minus10);

        assertEquals(GrowthDecelerationStatus.NOT_DECELERATING, result.status());
        assertEquals(Optional.empty(), result.decelerationGap());
    }

    @Test
    void evaluateReturnsDeceleratingWhenShortCagrIsLower() {
        // t1=1.331, t1-3=1.00 → short CAGR = 0.10
        // t1=1.331, t1-10=0.50 → long CAGR = (2.662)^(1/10)-1 ≈ 0.1029 > short → 둔화
        TtmDividendSummary t1 = complete("1.331");
        TtmDividendSummary t1Minus3 = complete("1.00");
        TtmDividendSummary t1Minus10 = complete("0.50");

        GrowthDecelerationResult result = DividendGrowthCalculator.evaluate(t1, t1Minus3, t1Minus10);

        assertEquals(GrowthDecelerationStatus.DECELERATING, result.status());
        assertTrue(result.decelerationGap().isPresent());
        assertTrue(result.decelerationGap().get().signum() > 0);
    }

    @Test
    void evaluateReturnsInsufficientDataWhenLongTermWindowIsIncomplete() {
        TtmDividendSummary t1 = complete("1.331");
        TtmDividendSummary t1Minus3 = complete("1.00");
        TtmDividendSummary t1Minus10 = new TtmDividendSummary(new BigDecimal("0.5"), new BigDecimal("0.6"), 2, 4);

        GrowthDecelerationResult result = DividendGrowthCalculator.evaluate(t1, t1Minus3, t1Minus10);

        assertEquals(GrowthDecelerationStatus.INSUFFICIENT_DATA, result.status());
        assertEquals(Optional.empty(), result.cagrLong());
        assertEquals(Optional.empty(), result.decelerationGap());
    }

    private static TtmDividendSummary complete(String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new TtmDividendSummary(value, value, 4, 4);
    }

    private static void assertApprox(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.subtract(actual).abs().compareTo(TOLERANCE) < 0,
                () -> "expected=%s actual=%s".formatted(expected, actual));
    }
}
