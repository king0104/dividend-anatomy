package com.dividendanatomy.domain.volatility;

import com.dividendanatomy.domain.yield.TtmDividendSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DividendVolatilityCalculatorTest {

    private static final MathContext MC = MathContext.DECIMAL64;

    /**
     * 손계산: 성장률이 0.10, 0.20을 5번씩 번갈아 나오게 D_0=1.00부터
     * *1.10/*1.20을 번갈아 곱해서 11개 지점 생성.
     * mean=0.15, 편차=±0.05, sum_sq=10*0.0025=0.025 (전부 정확한 손계산값).
     * variance=0.025/9, stdDev=variance.sqrt(MC)를 테스트에서 동일한
     * 방식으로 직접 계산해 대조한다.
     */
    @Test
    void computesSampleStandardDeviationForHandCalculatedGrowthRates() {
        List<TtmDividendSummary> summaries = buildFromMultipliers(
                new BigDecimal("1.00"),
                "1.10", "1.20", "1.10", "1.20", "1.10", "1.20", "1.10", "1.20", "1.10", "1.20");

        VolatilityResult result = DividendVolatilityCalculator.evaluate(summaries);

        assertEquals(VolatilityStatus.COMPLETE, result.status());
        assertTrue(result.meanGrowthRate().isPresent());
        assertTrue(result.standardDeviation().isPresent());

        assertEquals(0, new BigDecimal("0.15").compareTo(result.meanGrowthRate().get()));

        BigDecimal expectedVariance = new BigDecimal("0.025").divide(new BigDecimal("9"), MC);
        BigDecimal expectedStdDev = expectedVariance.sqrt(MC);
        assertEquals(0, expectedStdDev.compareTo(result.standardDeviation().get()));

        // N(10)으로 나눈 값과는 달라야 한다 — 표본(N-1) 공식 확인 (예측 #17 대응)
        BigDecimal populationVariance = new BigDecimal("0.025").divide(BigDecimal.TEN, MC);
        assertTrue(populationVariance.sqrt(MC).compareTo(result.standardDeviation().get()) != 0);
    }

    /**
     * 이론상 정확히 0이어야 하지만, D_i를 매번 *1.05로 누적 계산하는
     * 과정에서 MathContext.DECIMAL64 반올림이 16번째 유효숫자 근처에서
     * 미세하게 흔들려(docs/ai-defects/02-mathcontext-precision-drift.md와
     * 동일한 현상) 완전히 0이 아니라 아주 작은 값이 나올 수 있다 —
     * 허용오차로 비교한다.
     */
    @Test
    void standardDeviationIsApproximatelyZeroWhenAllGrowthRatesAreEqual() {
        String[] multipliers = new String[10];
        for (int i = 0; i < 10; i++) {
            multipliers[i] = "1.05";
        }
        List<TtmDividendSummary> summaries = buildFromMultipliers(new BigDecimal("1.00"), multipliers);

        VolatilityResult result = DividendVolatilityCalculator.evaluate(summaries);

        assertEquals(VolatilityStatus.COMPLETE, result.status());
        BigDecimal tolerance = new BigDecimal("1E-10");
        assertTrue(result.standardDeviation().get().abs().compareTo(tolerance) < 0,
                () -> "stdDev=%s".formatted(result.standardDeviation().get()));
    }

    /** 11개가 아니라 10개만 주어지면(예측 #16이 걱정한 off-by-one) 계산하지 않는다. */
    @Test
    void returnsInsufficientDataWhenExactlyElevenPointsAreNotProvided() {
        List<TtmDividendSummary> tenPoints = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tenPoints.add(complete("1.00"));
        }

        VolatilityResult result = DividendVolatilityCalculator.evaluate(tenPoints);

        assertEquals(VolatilityStatus.INSUFFICIENT_DATA, result.status());
        assertTrue(result.meanGrowthRate().isEmpty());
        assertTrue(result.standardDeviation().isEmpty());
    }

    @Test
    void returnsInsufficientDataWhenAnyWindowIsIncomplete() {
        List<TtmDividendSummary> summaries = buildFromMultipliers(new BigDecimal("1.00"), repeat("1.05", 10));
        summaries.set(5, new TtmDividendSummary(new BigDecimal("1.10"), new BigDecimal("1.20"), 3, 4));

        VolatilityResult result = DividendVolatilityCalculator.evaluate(summaries);

        assertEquals(VolatilityStatus.INSUFFICIENT_DATA, result.status());
    }

    @Test
    void returnsInsufficientDataWhenAnEarlierAmountIsZero() {
        List<TtmDividendSummary> summaries = buildFromMultipliers(new BigDecimal("1.00"), repeat("1.05", 10));
        summaries.set(3, new TtmDividendSummary(BigDecimal.ZERO, BigDecimal.ZERO, 4, 4));

        VolatilityResult result = DividendVolatilityCalculator.evaluate(summaries);

        assertEquals(VolatilityStatus.INSUFFICIENT_DATA, result.status());
    }

    private static String[] repeat(String value, int times) {
        String[] result = new String[times];
        for (int i = 0; i < times; i++) {
            result[i] = value;
        }
        return result;
    }

    private static List<TtmDividendSummary> buildFromMultipliers(BigDecimal base, String... multipliers) {
        List<TtmDividendSummary> summaries = new ArrayList<>();
        BigDecimal current = base;
        summaries.add(complete(current));
        for (String multiplier : multipliers) {
            current = current.multiply(new BigDecimal(multiplier), MC);
            summaries.add(complete(current));
        }
        return summaries;
    }

    private static TtmDividendSummary complete(String amount) {
        return complete(new BigDecimal(amount));
    }

    private static TtmDividendSummary complete(BigDecimal amount) {
        return new TtmDividendSummary(amount, amount, 4, 4);
    }
}
