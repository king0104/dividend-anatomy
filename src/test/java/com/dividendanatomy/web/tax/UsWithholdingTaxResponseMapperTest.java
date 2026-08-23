package com.dividendanatomy.web.tax;

import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.tax.NetDividendEntry;
import com.dividendanatomy.domain.tax.NetDividendSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UsWithholdingTaxResponseMapperTest {

    @Test
    void mapsFieldsAndAlwaysIncludesTaxNotice() {
        NetDividendEntry entry = new NetDividendEntry(
                LocalDate.parse("2026-06-15"), new BigDecimal("1.00"), new BigDecimal("0.85"), DividendType.REGULAR);
        NetDividendSummary summary = new NetDividendSummary(List.of(entry));

        NetDividendSummaryResponse response = UsWithholdingTaxResponseMapper.toResponse("KO", summary);

        assertEquals("KO", response.tickerSymbol());
        assertFalse(response.taxNotice().isBlank());
        assertEquals(1, response.entries().size());
        assertEquals(0, new BigDecimal("0.85").compareTo(response.entries().get(0).netAmount()));
    }

    @Test
    void mapsEmptySummaryButStillIncludesTaxNotice() {
        NetDividendSummaryResponse response = UsWithholdingTaxResponseMapper.toResponse("EMPTY", new NetDividendSummary(List.of()));

        assertFalse(response.taxNotice().isBlank());
        assertEquals(0, response.entries().size());
    }
}
