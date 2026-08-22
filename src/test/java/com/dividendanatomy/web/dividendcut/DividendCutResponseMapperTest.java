package com.dividendanatomy.web.dividendcut;

import com.dividendanatomy.domain.dividendcut.CutComparisonResult;
import com.dividendanatomy.domain.dividendcut.CutComparisonStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DividendCutResponseMapperTest {

    @Test
    void roundsDecreasePercentOnlyForCutStatus() {
        CutComparisonResult cut = new CutComparisonResult(
                LocalDate.parse("2025-04-01"), CutComparisonStatus.CUT,
                new BigDecimal("4.00"), new BigDecimal("3.60"), new BigDecimal("10.125"));
        CutComparisonResult normal = new CutComparisonResult(
                LocalDate.parse("2025-07-01"), CutComparisonStatus.NORMAL,
                new BigDecimal("3.60"), new BigDecimal("4.00"), null);
        CutComparisonResult incomplete = new CutComparisonResult(
                LocalDate.parse("2025-10-01"), CutComparisonStatus.INCOMPLETE,
                new BigDecimal("4.00"), new BigDecimal("3.00"), null);

        DividendCutResponse response = DividendCutResponseMapper.toResponse("KO", List.of(cut, normal, incomplete));

        assertEquals("KO", response.tickerSymbol());
        assertEquals(3, response.comparisons().size());

        DividendCutResponse.CutEventDto cutDto = response.comparisons().get(0);
        assertEquals(0, new BigDecimal("10.13").compareTo(cutDto.decreasePercent()));
        assertEquals(0, new BigDecimal("4.00").compareTo(cutDto.previousTtmAmount()));
        assertEquals(0, new BigDecimal("3.60").compareTo(cutDto.currentTtmAmount()));

        assertNull(response.comparisons().get(1).decreasePercent());
        assertNull(response.comparisons().get(2).decreasePercent());
    }
}
