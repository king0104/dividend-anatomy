package com.dividendanatomy.ingestion.twelvedata;

import com.dividendanatomy.domain.market.ExchangeRate;
import com.dividendanatomy.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class TwelveDataFxIngestionServiceTest {

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    private static TwelveDataClient stubClient(TwelveDataTimeSeriesResponse fixedResponse) {
        return new TwelveDataClient(RestClient.builder(), "http://unused", "unused") {
            @Override
            public TwelveDataTimeSeriesResponse fetchDailyTimeSeries(String symbol, LocalDate startDate, LocalDate endDate) {
                return fixedResponse;
            }
        };
    }

    private static TwelveDataTimeSeriesResponse responseWith(String datetime, String rate) {
        return new TwelveDataTimeSeriesResponse(
                new TwelveDataMeta("USD/KRW", "1day", "Korean Won"),
                List.of(new TwelveDataBar(datetime, new BigDecimal(rate))),
                "ok");
    }

    @Test
    void insertsNewRateWhenNoneExists() {
        TwelveDataFxIngestionService service =
                new TwelveDataFxIngestionService(stubClient(responseWith("2026-08-21", "1386.16314")), exchangeRateRepository);

        int count = service.ingest("USD", "KRW", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-22"));

        assertEquals(1, count);
        ExchangeRate saved = exchangeRateRepository
                .findByFromCurrencyAndToCurrencyAndDate("USD", "KRW", LocalDate.parse("2026-08-21"))
                .orElseThrow();
        assertEquals(0, new BigDecimal("1386.16314").compareTo(saved.getRate()));
    }

    @Test
    void skipsAlreadyIngestedRateInsteadOfDuplicating() {
        TwelveDataFxIngestionService service =
                new TwelveDataFxIngestionService(stubClient(responseWith("2026-08-21", "1386.16314")), exchangeRateRepository);

        int firstRun = service.ingest("USD", "KRW", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-22"));
        int secondRun = service.ingest("USD", "KRW", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-22"));

        assertEquals(1, firstRun);
        assertEquals(0, secondRun);
        assertEquals(1, exchangeRateRepository.findAll().size());
    }
}
