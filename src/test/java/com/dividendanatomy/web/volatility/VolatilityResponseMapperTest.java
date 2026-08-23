package com.dividendanatomy.web.volatility;

import com.dividendanatomy.domain.volatility.VolatilityResult;
import com.dividendanatomy.domain.volatility.VolatilityStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VolatilityResponseMapperTest {

    @Test
    void roundsMeanAndStdDevToTwoDecimalsWhenComplete() {
        VolatilityResult result = new VolatilityResult(
                VolatilityStatus.COMPLETE,
                Optional.of(new BigDecimal("0.04346529726822894")),
                Optional.of(new BigDecimal("0.01191499148818899")));

        VolatilityResponse response = VolatilityResponseMapper.toResponse("KO", LocalDate.parse("2026-08-22"), result);

        assertEquals("KO", response.tickerSymbol());
        assertEquals(VolatilityStatus.COMPLETE, response.status());
        assertEquals(0, new BigDecimal("4.35").compareTo(response.meanGrowthRatePercent()));
        assertEquals(0, new BigDecimal("1.19").compareTo(response.standardDeviationPercent()));
    }

    @Test
    void bothFieldsAreNullWhenInsufficientData() {
        VolatilityResult result = new VolatilityResult(VolatilityStatus.INSUFFICIENT_DATA, Optional.empty(), Optional.empty());

        VolatilityResponse response = VolatilityResponseMapper.toResponse("YOUNG", LocalDate.parse("2026-08-22"), result);

        assertNull(response.meanGrowthRatePercent());
        assertNull(response.standardDeviationPercent());
    }
}
