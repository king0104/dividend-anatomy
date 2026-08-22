package com.dividendanatomy.ingestion.twelvedata;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.PriceBarRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TwelveDataClient는 실제 HTTP를 안 타게 서브클래스로 오버라이드해서
 * 고정 응답을 돌려주는 스텁으로 대체한다 (RestClient용 fake HTTP 계층을
 * 직접 만드는 대신 — MassiveClient/TwelveDataClient의 URL 조립 자체는
 * 이번 증분에서 자동 테스트하지 않고, 실제 API 수동 호출로만 확인했다는
 * 점을 감안해야 함).
 */
@DataJpaTest
class TwelveDataPriceIngestionServiceTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private PriceBarRepository priceBarRepository;

    private static TwelveDataClient stubClient(TwelveDataTimeSeriesResponse fixedResponse) {
        return new TwelveDataClient(RestClient.builder(), "http://unused", "unused") {
            @Override
            public TwelveDataTimeSeriesResponse fetchDailyTimeSeries(String symbol, LocalDate startDate, LocalDate endDate) {
                return fixedResponse;
            }
        };
    }

    private static TwelveDataTimeSeriesResponse responseWith(String datetime, String close) {
        return new TwelveDataTimeSeriesResponse(
                new TwelveDataMeta("NVDA", "1day", "USD"),
                List.of(new TwelveDataBar(datetime, new BigDecimal(close))),
                "ok");
    }

    @Test
    void insertsNewBarWhenNoneExists() {
        Ticker nvda = tickerRepository.save(new Ticker("NVDA", "NVIDIA Corporation", "USD"));
        TwelveDataPriceIngestionService service =
                new TwelveDataPriceIngestionService(stubClient(responseWith("2024-06-10", "121.79")), priceBarRepository);

        int count = service.ingest(nvda, LocalDate.parse("2024-06-01"), LocalDate.parse("2024-06-30"));

        assertEquals(1, count);
        Optional<PriceBar> saved = priceBarRepository.findByTickerAndDate(nvda, LocalDate.parse("2024-06-10"));
        assertEquals(0, new BigDecimal("121.79").compareTo(saved.orElseThrow().getClose()));
        assertEquals(DataSource.TWELVE_DATA, saved.get().getSource());
    }

    @Test
    void updatesExistingBarInsteadOfDuplicating() {
        Ticker nvda = tickerRepository.save(new Ticker("NVDA", "NVIDIA Corporation", "USD"));
        priceBarRepository.save(new PriceBar(nvda, LocalDate.parse("2024-06-10"), new BigDecimal("999.00"), DataSource.TWELVE_DATA));

        TwelveDataPriceIngestionService service =
                new TwelveDataPriceIngestionService(stubClient(responseWith("2024-06-10", "121.79")), priceBarRepository);
        int count = service.ingest(nvda, LocalDate.parse("2024-06-01"), LocalDate.parse("2024-06-30"));

        assertEquals(1, count);
        List<PriceBar> all = priceBarRepository.findAll();
        assertEquals(1, all.size()); // 중복 insert가 아니라 갱신
        assertEquals(0, new BigDecimal("121.79").compareTo(all.get(0).getClose()));
    }
}
