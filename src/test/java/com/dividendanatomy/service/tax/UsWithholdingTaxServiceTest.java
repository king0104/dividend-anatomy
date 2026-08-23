package com.dividendanatomy.service.tax;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.tax.NetDividendSummary;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UsWithholdingTaxServiceTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;

    private UsWithholdingTaxService service() {
        return new UsWithholdingTaxService(tickerRepository, dividendPaymentRepository);
    }

    @Test
    void returnsNetDividendsForUsdTicker() {
        Ticker ticker = tickerRepository.save(new Ticker("KO2", "Coca-Cola 2", "USD"));
        LocalDate date = LocalDate.parse("2026-06-15");
        dividendPaymentRepository.save(
                new DividendPayment(ticker, date, date, date, new BigDecimal("1.00"), DividendType.REGULAR, DataSource.MASSIVE));

        NetDividendSummary summary = service().getNetDividends("KO2");

        assertEquals(1, summary.entries().size());
        assertEquals(0, new BigDecimal("0.85").compareTo(summary.entries().get(0).netAmount()));
    }

    @Test
    void throwsWhenTickerCurrencyIsNotUsd() {
        tickerRepository.save(new Ticker("KRSTOCK", "Korean Stock", "KRW"));

        assertThrows(IllegalStateException.class, () -> service().getNetDividends("KRSTOCK"));
    }

    @Test
    void returnsEmptySummaryWhenNoDividendHistory() {
        tickerRepository.save(new Ticker("EMPTY2", "Empty Co 2", "USD"));

        NetDividendSummary summary = service().getNetDividends("EMPTY2");

        assertTrue(summary.entries().isEmpty());
    }

    @Test
    void throwsWhenTickerUnknown() {
        assertThrows(NoSuchElementException.class, () -> service().getNetDividends("NOPE4"));
    }
}
