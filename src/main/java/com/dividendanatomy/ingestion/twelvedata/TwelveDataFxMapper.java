package com.dividendanatomy.ingestion.twelvedata;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.ExchangeRate;

import java.time.LocalDate;
import java.util.List;

/** 순수 변환 — HTTP 없음, DB 없음. TwelveDataPriceMapper와 동일한 응답 스키마를 재사용한다. */
public final class TwelveDataFxMapper {

    private TwelveDataFxMapper() {
    }

    public static List<ExchangeRate> toExchangeRates(
            TwelveDataTimeSeriesResponse response, String fromCurrency, String toCurrency) {
        if (response.values() == null) {
            return List.of();
        }
        return response.values().stream()
                .map(bar -> new ExchangeRate(
                        fromCurrency, toCurrency, LocalDate.parse(bar.datetime()), bar.close(), DataSource.TWELVE_DATA))
                .toList();
    }
}
