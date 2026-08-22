package com.dividendanatomy.service.yield;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.TickerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class YieldDecompositionServiceTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private com.dividendanatomy.repository.PriceBarRepository priceBarRepository;

    @Autowired
    private com.dividendanatomy.repository.DividendPaymentRepository dividendPaymentRepository;

    @Autowired
    private com.dividendanatomy.repository.SplitEventRepository splitEventRepository;

    private YieldDecompositionService service() {
        TtmDividendAggregationService ttm =
                new TtmDividendAggregationService(dividendPaymentRepository, splitEventRepository);
        return new YieldDecompositionService(tickerRepository, priceBarRepository, ttm);
    }

    /**
     * YieldChangeDecomposerTest의 손계산 케이스(D0=3.00,P0=100.00,D1=3.60,P1=80.00)를
     * DB 시드로 그대로 재현. TTM 배당은 분기 4회 * 0.90 = 3.60(t1), 4회 * 0.75 = 3.00(t0)으로 구성.
     */
    @Test
    void reproducesHandCalculatedYieldChangeDecomposerCase() {
        Ticker ticker = new Ticker("TEST", "Test Co", "USD");
        ticker.setRegularPaymentsPerYear(4);
        ticker = tickerRepository.save(ticker);

        LocalDate t1 = LocalDate.parse("2026-08-22");
        LocalDate t0 = t1.minusYears(1);

        priceBarRepository.save(new PriceBar(ticker, t0, new BigDecimal("100.00"), DataSource.TWELVE_DATA));
        priceBarRepository.save(new PriceBar(ticker, t1, new BigDecimal("80.00"), DataSource.TWELVE_DATA));

        // t0 기준 TTM 창: [t0-12개월, t0] = 4회 * 0.75 = 3.00
        // 마지막 지급일을 t0-1일로 둬서, t1 창([t0, t1], 양 끝 포함)과 경계가 겹쳐
        // 이중으로 잡히지 않게 한다 (t0 = t1-12개월이 정확히 t1 창의 왼쪽 경계라
        // t0 그 날짜에 지급이 있으면 t0 창과 t1 창 양쪽에 다 걸린다).
        saveDividend(ticker, t0.minusMonths(9), "0.75");
        saveDividend(ticker, t0.minusMonths(6), "0.75");
        saveDividend(ticker, t0.minusMonths(3), "0.75");
        saveDividend(ticker, t0.minusDays(1), "0.75");

        // t1 기준 TTM 창: [t1-12개월, t1] = 4회 * 0.90 = 3.60
        saveDividend(ticker, t1.minusMonths(9), "0.90");
        saveDividend(ticker, t1.minusMonths(6), "0.90");
        saveDividend(ticker, t1.minusMonths(3), "0.90");
        saveDividend(ticker, t1, "0.90");

        YieldDecompositionResult result = service().decompose("TEST", t1);

        assertEquals(0, new BigDecimal("0.00825").compareTo(result.actual().priceContribution()));
        assertEquals(0, new BigDecimal("0.00675").compareTo(result.actual().dividendContribution()));
    }

    private void saveDividend(Ticker ticker, LocalDate date, String amount) {
        dividendPaymentRepository.save(
                new DividendPayment(ticker, date, date, date, new BigDecimal(amount), DividendType.REGULAR, DataSource.MASSIVE));
    }

    @Test
    void throwsWhenRegularPaymentsPerYearIsNotSet() {
        Ticker ticker = tickerRepository.save(new Ticker("NOFREQ", "No Frequency Co", "USD"));
        priceBarRepository.save(new PriceBar(ticker, LocalDate.parse("2025-08-22"), new BigDecimal("10"), DataSource.TWELVE_DATA));
        priceBarRepository.save(new PriceBar(ticker, LocalDate.parse("2026-08-22"), new BigDecimal("10"), DataSource.TWELVE_DATA));

        assertThrows(IllegalStateException.class,
                () -> service().decompose("NOFREQ", LocalDate.parse("2026-08-22")));
    }

    @Test
    void throwsWhenNoPriceDataAvailable() {
        Ticker ticker = new Ticker("NOPRICE", "No Price Co", "USD");
        ticker.setRegularPaymentsPerYear(4);
        tickerRepository.save(ticker);

        assertThrows(java.util.NoSuchElementException.class,
                () -> service().decompose("NOPRICE", LocalDate.parse("2026-08-22")));
    }

    @Test
    void flagsFallbackWhenExactPriceDateMissing() {
        Ticker ticker = new Ticker("FALLBACK", "Fallback Co", "USD");
        ticker.setRegularPaymentsPerYear(4);
        ticker = tickerRepository.save(ticker);

        LocalDate t1 = LocalDate.parse("2026-08-22");
        LocalDate t0 = t1.minusYears(1);

        // 정확히 t0, t1이 아니라 그보다 며칠 이전 값만 존재 (가장 가까운 값 조회 유도)
        priceBarRepository.save(new PriceBar(ticker, t0.minusDays(3), new BigDecimal("50.00"), DataSource.TWELVE_DATA));
        priceBarRepository.save(new PriceBar(ticker, t1.minusDays(2), new BigDecimal("60.00"), DataSource.TWELVE_DATA));

        YieldDecompositionResult result = service().decompose("FALLBACK", t1);

        assertTrue(result.usedFallbackPriceAtT0());
        assertTrue(result.usedFallbackPriceAtT1());
        assertFalse(result.annualized().isPresent());
    }
}
