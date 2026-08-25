package com.dividendanatomy.domain.safety;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DividendSafetyScoreCalculatorTest {

    /**
     * 손계산 (docs/decisions/14 "손계산 예시"와 동일한 값):
     * 배당성향 62.5% -> 20*(1.00-0.625)/(1.00-0.60) = 20*0.375/0.40 = 18.75
     * FCF대비배당 165.7% -> 위험 경계(100%) 이상이라 0(클램프)
     * ROE 42% -> 안전 경계(15%) 이상이라 20(클램프)
     * D/E 1.5배 -> 20*(2.0-1.5)/(2.0-1.0) = 20*0.5/1.0 = 10
     * 이자보상배율 10.67배 -> 안전 경계(3.0배) 이상이라 20(클램프)
     * 합계 = 18.75+0+20+10+20 = 68.75 -> GREEN(>=61)
     */
    @Test
    void handCalculated_mixedIndicators() {
        DividendSafetyScoreResult result = DividendSafetyScoreCalculator.calculate(
                new BigDecimal("0.625"),
                new BigDecimal("1.657"),
                new BigDecimal("0.42"),
                new BigDecimal("1.5"),
                new BigDecimal("10.67"));

        assertBigDecimalEquals(new BigDecimal("18.75"), result.payoutSubScore());
        assertBigDecimalEquals(BigDecimal.ZERO, result.fcfPayoutSubScore());
        assertBigDecimalEquals(new BigDecimal("20"), result.returnOnEquitySubScore());
        assertBigDecimalEquals(new BigDecimal("10"), result.debtToEquitySubScore());
        assertBigDecimalEquals(new BigDecimal("20"), result.interestCoverageSubScore());
        assertBigDecimalEquals(new BigDecimal("68.75"), result.totalScore());
        assertEquals(SafetyBand.GREEN, result.band());
    }

    @Test
    void everyIndicatorAtSafeEdge_scoresMaximum100() {
        DividendSafetyScoreResult result = DividendSafetyScoreCalculator.calculate(
                new BigDecimal("0.60"), new BigDecimal("0.70"), new BigDecimal("0.15"),
                new BigDecimal("1.00"), new BigDecimal("3.00"));

        assertBigDecimalEquals(new BigDecimal("100"), result.totalScore());
        assertEquals(SafetyBand.GREEN, result.band());
    }

    @Test
    void everyIndicatorAtRiskEdge_scoresMinimumZero() {
        DividendSafetyScoreResult result = DividendSafetyScoreCalculator.calculate(
                new BigDecimal("1.00"), new BigDecimal("1.00"), BigDecimal.ZERO,
                new BigDecimal("2.00"), new BigDecimal("1.50"));

        assertBigDecimalEquals(BigDecimal.ZERO, result.totalScore());
        assertEquals(SafetyBand.RED, result.band());
    }

    @Test
    void beyondRiskEdge_stillClampsToZero_notNegative() {
        DividendSafetyScoreResult result = DividendSafetyScoreCalculator.calculate(
                new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("-0.50"),
                new BigDecimal("10.00"), new BigDecimal("0.10"));

        assertBigDecimalEquals(BigDecimal.ZERO, result.totalScore());
        assertEquals(SafetyBand.RED, result.band());
    }

    @Test
    void bandBoundary_exactlyForty_isRed() {
        assertEquals(SafetyBand.RED, bandForFlatSubScore("8"));
    }

    @Test
    void bandBoundary_exactlyFortyOne_isYellow() {
        assertEquals(SafetyBand.YELLOW, bandForFlatSubScore("8.2"));
    }

    @Test
    void bandBoundary_exactlySixty_isYellow() {
        assertEquals(SafetyBand.YELLOW, bandForFlatSubScore("12"));
    }

    @Test
    void bandBoundary_exactlySixtyOne_isGreen() {
        assertEquals(SafetyBand.GREEN, bandForFlatSubScore("12.2"));
    }

    /** 5개 지표를 전부 같은 값(safeEdge와 riskEdge 사이 중간 어딘가)으로 둬서 총점 = subScore*5가 되게 만든다. */
    private static SafetyBand bandForFlatSubScore(String desiredSubScorePerIndicator) {
        // ROE는 higherIsBetter라 안전=0.15, 위험=0.00 사이에서 subScore/20 비율만큼 위치를 잡는다.
        BigDecimal ratio = new BigDecimal(desiredSubScorePerIndicator).divide(new BigDecimal("20"), 10, RoundingMode.HALF_UP);
        BigDecimal roe = ratio.multiply(new BigDecimal("0.15"));
        BigDecimal payout = new BigDecimal("1.00").subtract(ratio.multiply(new BigDecimal("0.40")));
        BigDecimal fcf = new BigDecimal("1.00").subtract(ratio.multiply(new BigDecimal("0.30")));
        BigDecimal debt = new BigDecimal("2.00").subtract(ratio.multiply(new BigDecimal("1.00")));
        BigDecimal coverage = new BigDecimal("1.50").add(ratio.multiply(new BigDecimal("1.50")));
        return DividendSafetyScoreCalculator.calculate(payout, fcf, roe, debt, coverage).band();
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected=%s actual=%s".formatted(expected, actual));
    }
}
