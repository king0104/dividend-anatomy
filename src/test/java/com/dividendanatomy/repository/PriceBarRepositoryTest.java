package com.dividendanatomy.repository;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class PriceBarRepositoryTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private PriceBarRepository priceBarRepository;

    private Ticker ko;
    private Ticker other;

    private void seed() {
        ko = tickerRepository.save(new Ticker("KO", "The Coca-Cola Company", "USD"));
        other = tickerRepository.save(new Ticker("PEP", "PepsiCo", "USD"));

        priceBarRepository.save(bar(ko, "2026-01-01", "60.00"));
        priceBarRepository.save(bar(ko, "2026-01-05", "61.00"));
        priceBarRepository.save(bar(ko, "2026-01-10", "62.00"));
        // 다른 티커의 데이터가 섞여 있어도 결과에 영향 없어야 함
        priceBarRepository.save(bar(other, "2026-01-06", "999.00"));
    }

    private PriceBar bar(Ticker ticker, String date, String close) {
        return new PriceBar(ticker, LocalDate.parse(date), new BigDecimal(close), DataSource.TWELVE_DATA);
    }

    @Test
    void findsNearestPriorBarWhenExactDateMissing() {
        seed();

        Optional<PriceBar> result = priceBarRepository
                .findTopByTickerAndDateLessThanEqualOrderByDateDesc(ko, LocalDate.parse("2026-01-07"));

        assertTrue(result.isPresent());
        assertEquals(LocalDate.parse("2026-01-05"), result.get().getDate());
        assertEquals(0, new BigDecimal("61.00").compareTo(result.get().getClose()));
    }

    @Test
    void findsExactDateWhenAvailable() {
        seed();

        Optional<PriceBar> result = priceBarRepository
                .findTopByTickerAndDateLessThanEqualOrderByDateDesc(ko, LocalDate.parse("2026-01-01"));

        assertTrue(result.isPresent());
        assertEquals(LocalDate.parse("2026-01-01"), result.get().getDate());
    }

    @Test
    void returnsEmptyWhenRequestedDateIsBeforeAllData() {
        seed();

        Optional<PriceBar> result = priceBarRepository
                .findTopByTickerAndDateLessThanEqualOrderByDateDesc(ko, LocalDate.parse("2025-12-01"));

        assertTrue(result.isEmpty());
    }

    @Test
    void neverReturnsAnotherTickersBar() {
        seed();

        Optional<PriceBar> result = priceBarRepository
                .findTopByTickerAndDateLessThanEqualOrderByDateDesc(ko, LocalDate.parse("2026-01-06"));

        assertTrue(result.isPresent());
        assertEquals("KO", result.get().getTicker().getSymbol());
    }

    @Test
    void findLatestOnOrBeforeForTickersReturnsOneNearestBarPerTicker() {
        seed();

        List<PriceBar> results = priceBarRepository
                .findLatestOnOrBeforeForTickers(List.of(ko, other), LocalDate.parse("2026-01-07"));

        assertEquals(2, results.size());
        Map<String, PriceBar> bySymbol = results.stream()
                .collect(Collectors.toMap(p -> p.getTicker().getSymbol(), p -> p));
        // KO의 01-10 bar는 cutoff(01-07) 이후라 제외되고, 그 이전 최근값(01-05)이 나와야 함
        assertEquals(LocalDate.parse("2026-01-05"), bySymbol.get("KO").getDate());
        assertEquals(LocalDate.parse("2026-01-06"), bySymbol.get("PEP").getDate());
    }

    @Test
    void findLatestOnOrBeforeForTickersOmitsTickerWithNoBarBeforeCutoff() {
        seed();
        Ticker tooNew = tickerRepository.save(new Ticker("TOONEW", "Too New Co", "USD"));
        priceBarRepository.save(bar(tooNew, "2026-02-01", "10.00")); // cutoff 이후 데이터만 존재

        List<PriceBar> results = priceBarRepository
                .findLatestOnOrBeforeForTickers(List.of(ko, tooNew), LocalDate.parse("2026-01-07"));

        assertEquals(1, results.size());
        assertEquals("KO", results.get(0).getTicker().getSymbol());
    }
}
