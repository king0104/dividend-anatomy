package com.dividendanatomy.domain.timemachine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeMachineSimulatorTest {

    /**
     * 손계산 (가격 100 고정, 배당 2 고정, 2년, 일시불 1000):
     * sharesReinvest_0 = 1000/100 = 10, sharesNoReinvest_0 = 10
     * 1년차: dividendReceived = 10*2 = 20 -> newShares = 20/100 = 0.2 -> sharesReinvest = 10.2
     *        cashNoReinvest += 10*2 = 20
     * 2년차: dividendReceived = 10.2*2 = 20.4 -> newShares = 20.4/100 = 0.204 -> sharesReinvest = 10.404
     *        cashNoReinvest += 10*2 = 20 (합 40)
     * finalValueReinvest = 10.404*100 = 1040.4
     * finalValueNoReinvest = 10*100 + 40 = 1040
     * difference = 0.4, totalReturnRatio = (1040.4-1000)/1000 = 0.0404
     * 가격이 안 움직여도 재투자 쪽이 "배당이 배당을 낳는" 복리 효과로 더 크다.
     */
    @Test
    void handCalculated_flatPriceLumpSum() {
        List<YearlyCheckpoint> checkpoints = List.of(
                new YearlyCheckpoint(LocalDate.parse("2025-08-25"), bd("2"), bd("100")),
                new YearlyCheckpoint(LocalDate.parse("2026-08-25"), bd("2"), bd("100")));

        TimeMachineResult result = TimeMachineSimulator.simulate(
                bd("100"), checkpoints, bd("1000"), BigDecimal.ZERO, InvestMode.LUMP_SUM);

        assertBigDecimalEquals(bd("1040.4"), result.finalValueReinvestUsd());
        assertBigDecimalEquals(bd("1040"), result.finalValueNoReinvestUsd());
        assertBigDecimalEquals(bd("0.4"), result.differenceUsd());
        assertBigDecimalEquals(bd("0.0404"), result.totalReturnRatio());
        assertEquals(2, result.yearlySeries().size());
    }

    /**
     * 손계산 (가격 100->110->121, 배당 없음, 2년, 일시불 1000):
     * 배당이 0이면 재투자할 게 없으니 두 시나리오가 항상 같아야 한다.
     * sharesReinvest = sharesNoReinvest = 1000/100 = 10 (변화 없음)
     * finalValueReinvest = finalValueNoReinvest = 10*121 = 1210
     */
    @Test
    void noDividend_reinvestAndNoReinvestAreIdentical() {
        List<YearlyCheckpoint> checkpoints = List.of(
                new YearlyCheckpoint(LocalDate.parse("2025-08-25"), BigDecimal.ZERO, bd("110")),
                new YearlyCheckpoint(LocalDate.parse("2026-08-25"), BigDecimal.ZERO, bd("121")));

        TimeMachineResult result = TimeMachineSimulator.simulate(
                bd("100"), checkpoints, bd("1000"), BigDecimal.ZERO, InvestMode.LUMP_SUM);

        assertBigDecimalEquals(bd("1210"), result.finalValueReinvestUsd());
        assertBigDecimalEquals(bd("1210"), result.finalValueNoReinvestUsd());
        assertBigDecimalEquals(BigDecimal.ZERO, result.differenceUsd());
    }

    @Test
    void rejectsEmptyCheckpoints() {
        assertThrows(IllegalArgumentException.class,
                () -> TimeMachineSimulator.simulate(bd("100"), List.of(), bd("1000"), BigDecimal.ZERO, InvestMode.LUMP_SUM));
    }

    @Test
    void rejectsNonPositiveStartPrice() {
        List<YearlyCheckpoint> checkpoints = List.of(new YearlyCheckpoint(LocalDate.parse("2026-08-25"), bd("1"), bd("100")));
        assertThrows(IllegalArgumentException.class,
                () -> TimeMachineSimulator.simulate(BigDecimal.ZERO, checkpoints, bd("1000"), BigDecimal.ZERO, InvestMode.LUMP_SUM));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected=%s actual=%s".formatted(expected, actual));
    }
}
