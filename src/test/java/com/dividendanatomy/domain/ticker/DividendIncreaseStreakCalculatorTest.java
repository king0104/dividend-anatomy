package com.dividendanatomy.domain.ticker;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DividendIncreaseStreakCalculatorTest {

    @Test
    void countsFourConsecutiveIncreasingYears() {
        Map<Integer, BigDecimal> totals = Map.of(
                2020, new BigDecimal("1.00"),
                2021, new BigDecimal("1.10"),
                2022, new BigDecimal("1.20"),
                2023, new BigDecimal("1.30"),
                2024, new BigDecimal("1.40"));
        Map<Integer, Integer> counts = Map.of(2020, 4, 2021, 4, 2022, 4, 2023, 4, 2024, 4);

        DividendIncreaseStreakResult result = DividendIncreaseStreakCalculator.evaluate(totals, counts, 4, 2026);

        assertEquals(DividendIncreaseStreakStatus.CALCULATED, result.status());
        assertEquals(4, result.streakYears());
    }

    @Test
    void breaksStreakOnFlatYearRatherThanTreatingEqualAsIncrease() {
        Map<Integer, BigDecimal> totals = Map.of(
                2021, new BigDecimal("1.00"),
                2022, new BigDecimal("1.10"),
                2023, new BigDecimal("1.10"), // 동일 -> 증가 아님
                2024, new BigDecimal("1.20"));
        Map<Integer, Integer> counts = Map.of(2021, 4, 2022, 4, 2023, 4, 2024, 4);

        DividendIncreaseStreakResult result = DividendIncreaseStreakCalculator.evaluate(totals, counts, 4, 2026);

        // 2024>2023 증가(streak=1), 2023 vs 2022는 동일이라 여기서 멈춤
        assertEquals(1, result.streakYears());
    }

    @Test
    void ignoresInProgressCurrentYearEvenIfItsPartialTotalWouldLookLikeADecrease() {
        Map<Integer, BigDecimal> totals = Map.of(
                2023, new BigDecimal("1.00"),
                2024, new BigDecimal("1.10"),
                2025, new BigDecimal("1.20"),
                2026, new BigDecimal("0.30")); // 올해, 아직 1분기치만 들어옴 -> 절대 완결연도와 비교하면 안 됨
        Map<Integer, Integer> counts = Map.of(2023, 4, 2024, 4, 2025, 4, 2026, 1);

        DividendIncreaseStreakResult result = DividendIncreaseStreakCalculator.evaluate(totals, counts, 4, 2026);

        assertEquals(DividendIncreaseStreakStatus.CALCULATED, result.status());
        assertEquals(2, result.streakYears()); // 2025>2024, 2024>2023 -> 2, 2026은 제외
    }

    @Test
    void returnsInsufficientDataWhenFewerThanTwoCompleteYears() {
        Map<Integer, BigDecimal> totals = Map.of(2025, new BigDecimal("1.00"));
        Map<Integer, Integer> counts = Map.of(2025, 4);

        DividendIncreaseStreakResult result = DividendIncreaseStreakCalculator.evaluate(totals, counts, 4, 2026);

        assertEquals(DividendIncreaseStreakStatus.INSUFFICIENT_DATA, result.status());
    }

    @Test
    void stopsStreakAtGapYearRatherThanTreatingItAsADecrease() {
        Map<Integer, BigDecimal> totals = Map.of(
                2021, new BigDecimal("1.00"),
                2022, new BigDecimal("0.50"), // 구멍: 2건만 들어옴
                2023, new BigDecimal("1.10"),
                2024, new BigDecimal("1.20"));
        Map<Integer, Integer> counts = Map.of(2021, 4, 2022, 2, 2023, 4, 2024, 4);

        DividendIncreaseStreakResult result = DividendIncreaseStreakCalculator.evaluate(totals, counts, 4, 2026);

        // 2024>2023 증가(streak=1), 2023의 이전 연도(2022)가 구멍(count<4)이라 그 경계에서 멈춤
        assertEquals(1, result.streakYears());
    }
}
