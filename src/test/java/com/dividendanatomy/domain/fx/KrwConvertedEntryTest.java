package com.dividendanatomy.domain.fx;

import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.ExchangeRate;
import com.dividendanatomy.domain.tax.NetDividendEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KrwConvertedEntryTest {

    private static final NetDividendEntry USD_ENTRY = new NetDividendEntry(
            LocalDate.parse("2026-06-15"), new BigDecimal("1.00"), new BigDecimal("0.85"), DividendType.REGULAR);

    @Test
    void convertsExactlyWithRoundNumberRate() {
        ExchangeRate rate = new ExchangeRate("USD", "KRW", LocalDate.parse("2026-06-15"), new BigDecimal("1400"), DataSource.TWELVE_DATA);

        KrwConvertedEntry entry = KrwConvertedEntry.convert(USD_ENTRY, LocalDate.parse("2026-06-15"), Optional.of(rate));

        assertEquals(FxConversionStatus.CONVERTED, entry.status());
        assertEquals(0, new BigDecimal("1400").compareTo(entry.grossAmountKrw()));
        assertEquals(0, new BigDecimal("1190").compareTo(entry.netAmountKrw()));
    }

    @Test
    void roundsToWholeWonWithRealisticRate() {
        // 0.85 * 1386.16314 = 1178.238669 -> HALF_UP 반올림 -> 1178
        ExchangeRate rate = new ExchangeRate("USD", "KRW", LocalDate.parse("2026-08-21"), new BigDecimal("1386.16314"), DataSource.TWELVE_DATA);

        KrwConvertedEntry entry = KrwConvertedEntry.convert(USD_ENTRY, LocalDate.parse("2026-08-21"), Optional.of(rate));

        assertEquals(0, new BigDecimal("1178").compareTo(entry.netAmountKrw()));
    }

    @Test
    void marksPayDateMissingWhenPayDateIsNullAndDoesNotSubstituteExDividendDate() {
        KrwConvertedEntry entry = KrwConvertedEntry.convert(USD_ENTRY, null, Optional.empty());

        assertEquals(FxConversionStatus.PAY_DATE_MISSING, entry.status());
        assertNull(entry.exchangeRate());
        assertNull(entry.grossAmountKrw());
        assertNull(entry.netAmountKrw());
    }

    @Test
    void marksNoRateDataAvailableWhenPayDateExistsButNoRateFound() {
        KrwConvertedEntry entry = KrwConvertedEntry.convert(USD_ENTRY, LocalDate.parse("2005-01-01"), Optional.empty());

        assertEquals(FxConversionStatus.NO_RATE_DATA_AVAILABLE, entry.status());
        assertNull(entry.grossAmountKrw());
    }

    @Test
    void payDateMissingAndNoRateDataAvailableAreDistinctStatuses() {
        KrwConvertedEntry missingPayDate = KrwConvertedEntry.convert(USD_ENTRY, null, Optional.empty());
        KrwConvertedEntry noRateData = KrwConvertedEntry.convert(USD_ENTRY, LocalDate.parse("2005-01-01"), Optional.empty());

        assertEquals(FxConversionStatus.PAY_DATE_MISSING, missingPayDate.status());
        assertEquals(FxConversionStatus.NO_RATE_DATA_AVAILABLE, noRateData.status());
    }
}
