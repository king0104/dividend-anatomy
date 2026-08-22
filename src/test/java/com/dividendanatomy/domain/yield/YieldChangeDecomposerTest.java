package com.dividendanatomy.domain.yield;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YieldChangeDecomposerTest {

    private static final MathContext MC = MathContext.DECIMAL64;

    /**
     * 손계산: Y0 = 3.00/100.00 = 3%, Y1 = 3.60/80.00 = 4.5%, ΔY = 1.5%p
     * priceContribution    = (3.00+3.60)/2 * (1/80 - 1/100) = 3.30 * 0.0025    = 0.00825 (0.825%p)
     * dividendContribution = (3.60-3.00)/2 * (1/100 + 1/80) = 0.30 * 0.0225   = 0.00675 (0.675%p)
     * 합 = 0.015 = ΔY
     */
    @Test
    void handCalculated_priceDropAndDividendGrowth() {
        YieldContribution result = YieldChangeDecomposer.decompose(
                bd("3.00"), bd("100.00"), bd("3.60"), bd("80.00"));

        assertBigDecimalEquals(bd("0.00825"), result.priceContribution());
        assertBigDecimalEquals(bd("0.00675"), result.dividendContribution());
        assertBigDecimalEquals(bd("0.015"),
                result.priceContribution().add(result.dividendContribution()));
    }

    /**
     * 손계산: 이 프로젝트의 플래그십 시나리오 — 배당은 그대로인데 주가만 폭락.
     * Y0 = 2.00/50.00 = 4%, Y1 = 2.00/10.00 = 20%, ΔY = 16%p
     * 배당이 안 늘었으니 dividendContribution = 0, priceContribution이 ΔY 전부.
     */
    @Test
    void handCalculated_priceCrashOnlyDividendUnchanged() {
        YieldContribution result = YieldChangeDecomposer.decompose(
                bd("2.00"), bd("50.00"), bd("2.00"), bd("10.00"));

        assertBigDecimalEquals(BigDecimal.ZERO, result.dividendContribution());
        assertBigDecimalEquals(bd("0.16"), result.priceContribution());
    }

    /**
     * (1/p0), (1/p1)이 유한소수로 딱 안 떨어지는 값(예: 1/77.10)이 섞이면,
     * decompose()의 계산 경로(역수를 먼저 구하고 곱함)와 이 테스트가 직접
     * 구하는 d1/p1 - d0/p0 경로가 MathContext.DECIMAL64(유효숫자 16자리)
     * 반올림을 서로 다른 지점에서 적용하게 되어 마지막 자리에서 미세한
     * 오차가 생길 수 있다 — 실제로 겪은 문제 (docs/ai-defects/02 참고).
     * 항등식 자체는 대수적으로 정확하므로, 여기서는 아주 작은 허용오차로
     * 비교한다. 소수 2자리로 반올림해 화면에 노출하는 스펙 3절 기준으로는
     * 이 정도 오차는 절대 드러나지 않는다.
     */
    private static final BigDecimal IDENTITY_TOLERANCE = new BigDecimal("1E-10");

    @Test
    void identity_priceContributionPlusDividendContributionEqualsDeltaY() {
        BigDecimal[][] cases = {
                {bd("3.00"), bd("100.00"), bd("3.60"), bd("80.00")},
                {bd("2.00"), bd("50.00"), bd("2.00"), bd("10.00")},
                {bd("5.00"), bd("60.00"), bd("4.00"), bd("60.00")}, // 배당 감소, 가격 불변
                {bd("1.234"), bd("77.10"), bd("0.987"), bd("120.45")}, // 임의 값
        };

        for (BigDecimal[] c : cases) {
            BigDecimal d0 = c[0];
            BigDecimal p0 = c[1];
            BigDecimal d1 = c[2];
            BigDecimal p1 = c[3];

            YieldContribution result = YieldChangeDecomposer.decompose(d0, p0, d1, p1);
            BigDecimal deltaY = d1.divide(p1, MC).subtract(d0.divide(p0, MC), MC);
            BigDecimal sum = result.priceContribution().add(result.dividendContribution(), MC);

            BigDecimal diff = deltaY.subtract(sum).abs();
            assertTrue(diff.compareTo(IDENTITY_TOLERANCE) <= 0,
                    () -> "deltaY=%s sum=%s diff=%s (case d0=%s p0=%s d1=%s p1=%s)"
                            .formatted(deltaY, sum, diff, d0, p0, d1, p1));
        }
    }

    @Test
    void rejectsZeroOrNegativePrice() {
        assertThrows(IllegalArgumentException.class,
                () -> YieldChangeDecomposer.decompose(bd("1"), BigDecimal.ZERO, bd("1"), bd("10")));
        assertThrows(IllegalArgumentException.class,
                () -> YieldChangeDecomposer.decompose(bd("1"), bd("10"), bd("1"), bd("-5")));
    }

    @Test
    void decomposeAnnualized_skipsWhenEitherSideHasNoData() {
        TtmDividendSummary empty = new TtmDividendSummary(BigDecimal.ZERO, null, 0, 4);
        TtmDividendSummary complete = new TtmDividendSummary(bd("4.00"), bd("4.00"), 4, 4);

        assertTrue(YieldChangeDecomposer.decomposeAnnualized(empty, complete, bd("10"), bd("10")).isEmpty());
        assertTrue(YieldChangeDecomposer.decomposeAnnualized(complete, empty, bd("10"), bd("10")).isEmpty());
    }

    @Test
    void decomposeAnnualized_delegatesToDecomposeWhenBothSidesHaveData() {
        TtmDividendSummary t0 = new TtmDividendSummary(bd("2.25"), bd("3.00"), 3, 4);
        TtmDividendSummary t1 = new TtmDividendSummary(bd("3.60"), bd("3.60"), 4, 4);

        Optional<YieldContribution> annualized =
                YieldChangeDecomposer.decomposeAnnualized(t0, t1, bd("100.00"), bd("80.00"));
        YieldContribution expected = YieldChangeDecomposer.decompose(bd("3.00"), bd("100.00"), bd("3.60"), bd("80.00"));

        assertTrue(annualized.isPresent());
        assertBigDecimalEquals(expected.priceContribution(), annualized.get().priceContribution());
        assertBigDecimalEquals(expected.dividendContribution(), annualized.get().dividendContribution());
    }

    @Test
    void ttmDividendSummary_rejectsInconsistentState() {
        assertThrows(IllegalArgumentException.class,
                () -> new TtmDividendSummary(BigDecimal.ZERO, null, -1, 4)); // foundCount < 0
        assertThrows(IllegalArgumentException.class,
                () -> new TtmDividendSummary(BigDecimal.ZERO, bd("1.00"), 0, 4)); // foundCount=0인데 annualizedSum 있음
        assertThrows(IllegalArgumentException.class,
                () -> new TtmDividendSummary(BigDecimal.ZERO, null, 0, 0)); // expectedCount <= 0
    }

    /**
     * 실제 배당 캘린더는 91.25일 간격이 아니라서 롤링 12개월 창에 분기 배당이
     * 5번 들어가는 경우가 있다(KO 실데이터로 확인) — foundCount가 expectedCount를
     * 넘는 건 예외가 아니라 정상이고, isComplete()는 "부족하지 않음"만 본다.
     */
    @Test
    void ttmDividendSummary_allowsFoundCountToExceedExpectedCountAndMarksComplete() {
        TtmDividendSummary surplus = new TtmDividendSummary(bd("5.00"), bd("4.00"), 5, 4);
        assertTrue(surplus.isComplete());

        TtmDividendSummary gap = new TtmDividendSummary(bd("3.00"), bd("4.00"), 3, 4);
        assertFalse(gap.isComplete());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual),
                () -> "expected=%s actual=%s".formatted(expected, actual));
    }
}
