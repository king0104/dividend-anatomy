package com.dividendanatomy.service.dividenddisclosure;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.dividenddisclosure.SpecialDividendDisclosure;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;
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
class SpecialDividendDisclosureServiceTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;

    private SpecialDividendDisclosureService service() {
        return new SpecialDividendDisclosureService(tickerRepository, dividendPaymentRepository);
    }

    @Test
    void returnsPaymentsOrderedByExDividendDateWithSpecialExcluded() {
        Ticker ticker = tickerRepository.save(new Ticker("COST2", "Costco 2", "USD"));
        saveDividend(ticker, LocalDate.parse("2024-02-01"), new BigDecimal("1.02"), DividendType.REGULAR);
        saveDividend(ticker, LocalDate.parse("2023-12-27"), new BigDecimal("15"), DividendType.SPECIAL);
        saveDividend(ticker, LocalDate.parse("2023-08-24"), new BigDecimal("1.02"), DividendType.REGULAR);

        SpecialDividendDisclosure disclosure = service().getDisclosure("COST2");

        assertEquals(2, disclosure.regularCount());
        assertEquals(1, disclosure.specialCount());
        assertEquals(LocalDate.parse("2023-08-24"), disclosure.entries().get(0).exDividendDate());
        assertEquals(LocalDate.parse("2023-12-27"), disclosure.entries().get(1).exDividendDate());
        assertEquals(LocalDate.parse("2024-02-01"), disclosure.entries().get(2).exDividendDate());
        assertTrue(disclosure.entries().get(1).excluded());
    }

    @Test
    void returnsEmptyResultWhenNoDividendHistory() {
        tickerRepository.save(new Ticker("EMPTY", "Empty Co", "USD"));

        SpecialDividendDisclosure disclosure = service().getDisclosure("EMPTY");

        assertEquals(0, disclosure.regularCount());
        assertEquals(0, disclosure.specialCount());
        assertTrue(disclosure.entries().isEmpty());
    }

    @Test
    void throwsWhenTickerUnknown() {
        assertThrows(NoSuchElementException.class, () -> service().getDisclosure("NOPE3"));
    }

    private void saveDividend(Ticker ticker, LocalDate date, BigDecimal amount, DividendType type) {
        dividendPaymentRepository.save(new DividendPayment(ticker, date, date, date, amount, type, DataSource.MASSIVE));
    }
}
