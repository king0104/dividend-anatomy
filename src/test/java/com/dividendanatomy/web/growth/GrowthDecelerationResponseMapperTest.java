package com.dividendanatomy.web.growth;

import com.dividendanatomy.domain.growth.GrowthDecelerationResult;
import com.dividendanatomy.domain.growth.GrowthDecelerationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GrowthDecelerationResponseMapperTest {

    @Test
    void roundsCagrAndGapToTwoDecimalsWhenDecelerating() {
        GrowthDecelerationResult result = new GrowthDecelerationResult(
                Optional.of(new BigDecimal("0.049373944420963")),
                Optional.of(new BigDecimal("0.10125")),
                GrowthDecelerationStatus.DECELERATING,
                Optional.of(new BigDecimal("0.051876055579037")));

        GrowthDecelerationResponse response =
                GrowthDecelerationResponseMapper.toResponse("KO", LocalDate.parse("2026-08-22"), result);

        assertEquals("KO", response.tickerSymbol());
        assertEquals(GrowthDecelerationStatus.DECELERATING, response.status());
        assertEquals(0, new BigDecimal("4.94").compareTo(response.cagrShortPercent()));
        assertEquals(0, new BigDecimal("10.13").compareTo(response.cagrLongPercent()));
        assertEquals(0, new BigDecimal("5.19").compareTo(response.decelerationGapPercent()));
    }

    @Test
    void gapIsNullWhenNotDecelerating() {
        GrowthDecelerationResult result = new GrowthDecelerationResult(
                Optional.of(new BigDecimal("0.05")),
                Optional.of(new BigDecimal("0.04")),
                GrowthDecelerationStatus.NOT_DECELERATING,
                Optional.empty());

        GrowthDecelerationResponse response =
                GrowthDecelerationResponseMapper.toResponse("KO", LocalDate.parse("2026-08-22"), result);

        assertNull(response.decelerationGapPercent());
    }

    @Test
    void cagrLongIsNullWhenInsufficientData() {
        GrowthDecelerationResult result = new GrowthDecelerationResult(
                Optional.of(new BigDecimal("0.05")),
                Optional.empty(),
                GrowthDecelerationStatus.INSUFFICIENT_DATA,
                Optional.empty());

        GrowthDecelerationResponse response =
                GrowthDecelerationResponseMapper.toResponse("YOUNG", LocalDate.parse("2026-08-22"), result);

        assertNull(response.cagrLongPercent());
        assertNull(response.decelerationGapPercent());
    }
}
