package com.dividendanatomy.service.ticker;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.ticker.DividendIncreaseStreakStatus;
import com.dividendanatomy.domain.ticker.TickerSummary;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.PriceBarRepository;
import com.dividendanatomy.repository.SplitEventRepository;
import com.dividendanatomy.repository.TickerRepository;
import com.dividendanatomy.service.yield.TtmDividendAggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class TickerSummaryServiceTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;

    @Autowired
    private PriceBarRepository priceBarRepository;

    @Autowired
    private SplitEventRepository splitEventRepository;

    private TickerSummaryService service() {
        return new TickerSummaryService(
                tickerRepository, dividendPaymentRepository, priceBarRepository, splitEventRepository,
                new TtmDividendAggregationService(dividendPaymentRepository, splitEventRepository));
    }

    @Test
    void streakIsNotBrokenByInProgressCurrentYearPartialPayments() {
        Ticker ticker = tickerRepository.save(new Ticker("STREAKCO", "Streak Co", "USD"));
        ticker.setRegularPaymentsPerYear(4);
        ticker = tickerRepository.save(ticker);
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();

        // 4개년 연속 인상 완결 + 올해는 1분기치만(진행 중)
        seedYear(ticker, currentYear - 4, new BigDecimal("1.00"));
        seedYear(ticker, currentYear - 3, new BigDecimal("1.10"));
        seedYear(ticker, currentYear - 2, new BigDecimal("1.20"));
        seedYear(ticker, currentYear - 1, new BigDecimal("1.30"));
        dividendPaymentRepository.save(new DividendPayment(
                ticker, LocalDate.of(currentYear, 3, 1), LocalDate.of(currentYear, 3, 1), LocalDate.of(currentYear, 3, 15),
                new BigDecimal("0.10"), DividendType.REGULAR, DataSource.MASSIVE)); // 올해 1건만, 작년 전체보다 훨씬 적음

        priceBarRepository.save(new PriceBar(ticker, today, new BigDecimal("100.00"), DataSource.TWELVE_DATA));

        TickerSummary summary = service().summarize("STREAKCO");

        assertEquals(DividendIncreaseStreakStatus.CALCULATED, summary.streakResult().status());
        assertEquals(3, summary.streakResult().streakYears()); // (currentYear-1)>(currentYear-2)>(currentYear-3)>(currentYear-4)
    }

    @Test
    void throwsWhenRegularPaymentsPerYearNotSet() {
        tickerRepository.save(new Ticker("NOFREQ3", "No Frequency Co 3", "USD"));

        assertThrows(IllegalStateException.class, () -> service().summarize("NOFREQ3"));
    }

    @Test
    void throwsWhenTickerUnknown() {
        assertThrows(NoSuchElementException.class, () -> service().summarize("NOPE6"));
    }

    @Test
    void summarizeAllSkipsProblematicTickerButReturnsOthers() {
        Ticker good = tickerRepository.save(new Ticker("GOOD", "Good Co", "USD"));
        good.setRegularPaymentsPerYear(4);
        tickerRepository.save(good);
        dividendPaymentRepository.save(new DividendPayment(
                good, LocalDate.now().minusMonths(1), LocalDate.now().minusMonths(1), LocalDate.now().minusMonths(1),
                new BigDecimal("0.50"), DividendType.REGULAR, DataSource.MASSIVE));

        tickerRepository.save(new Ticker("BAD", "Bad Co", "USD")); // regularPaymentsPerYear 없음 -> 예외 유발

        List<TickerSummary> summaries = service().summarizeAll();

        assertTrue(summaries.stream().anyMatch(s -> s.symbol().equals("GOOD")));
        assertTrue(summaries.stream().noneMatch(s -> s.symbol().equals("BAD")));
    }

    private void seedYear(Ticker ticker, int year, BigDecimal amount) {
        dividendPaymentRepository.save(payment(ticker, LocalDate.of(year, 2, 1), amount));
        dividendPaymentRepository.save(payment(ticker, LocalDate.of(year, 5, 1), amount));
        dividendPaymentRepository.save(payment(ticker, LocalDate.of(year, 8, 1), amount));
        dividendPaymentRepository.save(payment(ticker, LocalDate.of(year, 11, 1), amount));
    }

    private DividendPayment payment(Ticker ticker, LocalDate date, BigDecimal amount) {
        return new DividendPayment(ticker, date, date, date, amount, DividendType.REGULAR, DataSource.MASSIVE);
    }
}
