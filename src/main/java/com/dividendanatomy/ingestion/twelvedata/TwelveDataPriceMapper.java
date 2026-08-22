package com.dividendanatomy.ingestion.twelvedata;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;

import java.time.LocalDate;
import java.util.List;

/** 순수 변환 — HTTP 없음, DB 없음. 테스트하기 쉽게 분리. */
public final class TwelveDataPriceMapper {

    private TwelveDataPriceMapper() {
    }

    public static List<PriceBar> toPriceBars(TwelveDataTimeSeriesResponse response, Ticker ticker) {
        if (response.values() == null) {
            return List.of();
        }
        return response.values().stream()
                .map(bar -> new PriceBar(ticker, LocalDate.parse(bar.datetime()), bar.close(), DataSource.TWELVE_DATA))
                .toList();
    }
}
