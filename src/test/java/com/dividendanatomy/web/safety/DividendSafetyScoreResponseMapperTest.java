package com.dividendanatomy.web.safety;

import com.dividendanatomy.domain.safety.DividendSafetyScoreResult;
import com.dividendanatomy.domain.safety.SafetyBand;
import com.dividendanatomy.service.safety.DividendSafetyScoreServiceResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DividendSafetyScoreResponseMapperTest {

    /**
     * 손계산(DividendSafetyScoreCalculatorTest.handCalculated_mixedIndicators와 동일 입력):
     * 배당성향 0.625 -> ×100 반올림 62.50, 서브스코어 18.75
     * FCF대비배당 1.657 -> 165.70, 서브스코어 0.00
     * ROE 0.42 -> 42.00, 서브스코어 20.00
     * D/E 1.5 -> 1.50(배수라 ×100 안 함), 서브스코어 10.00
     * 이자보상배율 10.67 -> 10.67, 서브스코어 20.00
     * 총점 68.75 -> HALF_UP 반올림 69
     */
    @Test
    void roundsPercentagesAndTotalScore() {
        DividendSafetyScoreResult domainResult = new DividendSafetyScoreResult(
                new BigDecimal("0.625"), new BigDecimal("18.75"),
                new BigDecimal("1.657"), BigDecimal.ZERO,
                new BigDecimal("0.42"), new BigDecimal("20"),
                new BigDecimal("1.5"), new BigDecimal("10"),
                new BigDecimal("10.67"), new BigDecimal("20"),
                new BigDecimal("68.75"), SafetyBand.GREEN);
        DividendSafetyScoreServiceResult serviceResult = new DividendSafetyScoreServiceResult(
                "SAFETYTEST", true, LocalDate.parse("2025-12-31"), domainResult);

        DividendSafetyScoreResponse response = DividendSafetyScoreResponseMapper.toResponse(serviceResult);

        assertEquals("SAFETYTEST", response.tickerSymbol());
        assertTrue(response.available());
        assertEquals(LocalDate.parse("2025-12-31"), response.fiscalDateEnding());
        assertBigDecimalEquals(new BigDecimal("69"), response.totalScore());
        assertEquals("GREEN", response.band());

        Map<String, DividendSafetyScoreResponse.SafetyIndicatorDto> byName = response.indicators().stream()
                .collect(java.util.stream.Collectors.toMap(DividendSafetyScoreResponse.SafetyIndicatorDto::name, d -> d));

        assertBigDecimalEquals(new BigDecimal("62.50"), byName.get("PAYOUT_RATIO").value());
        assertBigDecimalEquals(new BigDecimal("18.75"), byName.get("PAYOUT_RATIO").subScore());
        assertBigDecimalEquals(new BigDecimal("165.70"), byName.get("FCF_PAYOUT_RATIO").value());
        assertBigDecimalEquals(new BigDecimal("42.00"), byName.get("ROE").value());
        assertBigDecimalEquals(new BigDecimal("1.50"), byName.get("DEBT_TO_EQUITY").value());
        assertBigDecimalEquals(new BigDecimal("10.67"), byName.get("INTEREST_COVERAGE").value());
    }

    @Test
    void unavailable_leavesNumbersNull() {
        DividendSafetyScoreServiceResult serviceResult =
                new DividendSafetyScoreServiceResult("NOPE", false, null, null);

        DividendSafetyScoreResponse response = DividendSafetyScoreResponseMapper.toResponse(serviceResult);

        assertEquals("NOPE", response.tickerSymbol());
        assertEquals(false, response.available());
        assertNull(response.fiscalDateEnding());
        assertNull(response.indicators());
        assertNull(response.totalScore());
        assertNull(response.band());
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected=%s actual=%s".formatted(expected, actual));
    }
}
