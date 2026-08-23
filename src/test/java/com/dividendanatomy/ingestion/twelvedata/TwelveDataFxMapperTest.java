package com.dividendanatomy.ingestion.twelvedata;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.ExchangeRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwelveDataFxMapperTest {

    @Test
    void mapsForexResponseToExchangeRates() {
        TwelveDataTimeSeriesResponse response = new TwelveDataTimeSeriesResponse(
                new TwelveDataMeta("USD/KRW", "1day", "Korean Won"),
                List.of(
                        new TwelveDataBar("2026-08-21", new BigDecimal("1386.16314")),
                        new TwelveDataBar("2026-08-20", new BigDecimal("1394.27043"))),
                "ok");

        List<ExchangeRate> rates = TwelveDataFxMapper.toExchangeRates(response, "USD", "KRW");

        assertEquals(2, rates.size());
        ExchangeRate first = rates.stream().filter(r -> r.getDate().equals(LocalDate.parse("2026-08-21"))).findFirst().orElseThrow();
        assertEquals("USD", first.getFromCurrency());
        assertEquals("KRW", first.getToCurrency());
        assertEquals(0, new BigDecimal("1386.16314").compareTo(first.getRate()));
        assertEquals(DataSource.TWELVE_DATA, first.getSource());
    }

    @Test
    void returnsEmptyListWhenValuesIsNull() {
        TwelveDataTimeSeriesResponse response = new TwelveDataTimeSeriesResponse(
                new TwelveDataMeta("USD/KRW", "1day", "Korean Won"), null, "error");

        List<ExchangeRate> rates = TwelveDataFxMapper.toExchangeRates(response, "USD", "KRW");

        assertTrue(rates.isEmpty());
    }
}
