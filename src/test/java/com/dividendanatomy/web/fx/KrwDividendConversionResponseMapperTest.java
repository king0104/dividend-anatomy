package com.dividendanatomy.web.fx;

import com.dividendanatomy.domain.fx.FxConversionStatus;
import com.dividendanatomy.domain.fx.KrwConvertedEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KrwDividendConversionResponseMapperTest {

    @Test
    void mapsConvertedEntry() {
        KrwConvertedEntry entry = new KrwConvertedEntry(
                LocalDate.parse("2026-06-15"), new BigDecimal("1.00"), new BigDecimal("0.85"),
                FxConversionStatus.CONVERTED, new BigDecimal("1400"), new BigDecimal("1400"), new BigDecimal("1190"));

        KrwDividendConversionResponse response = KrwDividendConversionResponseMapper.toResponse("KO", List.of(entry));

        assertEquals("KO", response.tickerSymbol());
        assertEquals(FxConversionStatus.CONVERTED, response.entries().get(0).status());
        assertEquals(0, new BigDecimal("1190").compareTo(response.entries().get(0).netAmountKrw()));
    }

    @Test
    void mapsUnavailableEntryWithNullKrwFields() {
        KrwConvertedEntry entry = new KrwConvertedEntry(
                LocalDate.parse("2003-06-11"), new BigDecimal("0.22"), new BigDecimal("0.19"),
                FxConversionStatus.NO_RATE_DATA_AVAILABLE, null, null, null);

        KrwDividendConversionResponse response = KrwDividendConversionResponseMapper.toResponse("KO", List.of(entry));

        assertEquals(FxConversionStatus.NO_RATE_DATA_AVAILABLE, response.entries().get(0).status());
        assertNull(response.entries().get(0).netAmountKrw());
    }
}
