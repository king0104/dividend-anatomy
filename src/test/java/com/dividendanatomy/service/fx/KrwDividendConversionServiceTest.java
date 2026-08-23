package com.dividendanatomy.service.fx;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.fx.FxConversionStatus;
import com.dividendanatomy.domain.fx.KrwConvertedEntry;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.ExchangeRate;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.ExchangeRateRepository;
import com.dividendanatomy.repository.TickerRepository;
import com.dividendanatomy.service.tax.UsWithholdingTaxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class KrwDividendConversionServiceTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    private KrwDividendConversionService service() {
        return new KrwDividendConversionService(
                tickerRepository, dividendPaymentRepository, exchangeRateRepository,
                new UsWithholdingTaxService(tickerRepository, dividendPaymentRepository));
    }

    @Test
    void convertsWhenPayDateAndRateBothExist() {
        Ticker ticker = tickerRepository.save(new Ticker("KO3", "Coca-Cola 3", "USD"));
        LocalDate exDate = LocalDate.parse("2026-06-15");
        LocalDate payDate = LocalDate.parse("2026-07-01");
        dividendPaymentRepository.save(new DividendPayment(ticker, exDate, exDate, payDate, new BigDecimal("1.00"), DividendType.REGULAR, DataSource.MASSIVE));
        exchangeRateRepository.save(new ExchangeRate("USD", "KRW", payDate, new BigDecimal("1400"), DataSource.TWELVE_DATA));

        List<KrwConvertedEntry> entries = service().getKrwConvertedDividends("KO3");

        assertEquals(1, entries.size());
        assertEquals(FxConversionStatus.CONVERTED, entries.get(0).status());
        assertEquals(0, new BigDecimal("1190").compareTo(entries.get(0).netAmountKrw()));
    }

    @Test
    void marksPayDateMissingWhenPaymentHasNoPayDate() {
        Ticker ticker = tickerRepository.save(new Ticker("KO4", "Coca-Cola 4", "USD"));
        LocalDate exDate = LocalDate.parse("2026-06-15");
        dividendPaymentRepository.save(new DividendPayment(ticker, exDate, exDate, null, new BigDecimal("1.00"), DividendType.REGULAR, DataSource.MASSIVE));

        List<KrwConvertedEntry> entries = service().getKrwConvertedDividends("KO4");

        assertEquals(FxConversionStatus.PAY_DATE_MISSING, entries.get(0).status());
    }

    @Test
    void marksNoRateDataAvailableWhenPayDatePredatesAllStoredRates() {
        Ticker ticker = tickerRepository.save(new Ticker("KO5", "Coca-Cola 5", "USD"));
        LocalDate exDate = LocalDate.parse("2003-06-11");
        LocalDate payDate = LocalDate.parse("2003-06-25");
        dividendPaymentRepository.save(new DividendPayment(ticker, exDate, exDate, payDate, new BigDecimal("0.22"), DividendType.REGULAR, DataSource.MASSIVE));
        exchangeRateRepository.save(new ExchangeRate("USD", "KRW", LocalDate.parse("2007-10-11"), new BigDecimal("920"), DataSource.TWELVE_DATA));

        List<KrwConvertedEntry> entries = service().getKrwConvertedDividends("KO5");

        assertEquals(FxConversionStatus.NO_RATE_DATA_AVAILABLE, entries.get(0).status());
    }

    @Test
    void throwsWhenTickerCurrencyIsNotUsd() {
        tickerRepository.save(new Ticker("KRSTOCK2", "Korean Stock 2", "KRW"));

        assertThrows(IllegalStateException.class, () -> service().getKrwConvertedDividends("KRSTOCK2"));
    }

    @Test
    void throwsWhenTickerUnknown() {
        assertThrows(NoSuchElementException.class, () -> service().getKrwConvertedDividends("NOPE5"));
    }
}
