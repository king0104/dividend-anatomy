package com.dividendanatomy.service.ticker;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.SplitEvent;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

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

    @Test
    void summarizeAllMatchesIndividualSummarizeAcrossMultipleTickersWithSplits() {
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();

        Ticker multiA = tickerRepository.save(new Ticker("MULTIA", "Multi A Co", "USD"));
        multiA.setRegularPaymentsPerYear(4);
        multiA = tickerRepository.save(multiA);
        seedYear(multiA, currentYear - 4, new BigDecimal("1.00"));
        seedYear(multiA, currentYear - 3, new BigDecimal("1.10"));
        seedYear(multiA, currentYear - 2, new BigDecimal("1.20"));
        seedYear(multiA, currentYear - 1, new BigDecimal("1.30"));
        splitEventRepository.save(new SplitEvent(
                multiA, LocalDate.of(currentYear - 4, 6, 1), new BigDecimal("2"), DataSource.MASSIVE));
        priceBarRepository.save(new PriceBar(multiA, today, new BigDecimal("50.00"), DataSource.TWELVE_DATA));

        Ticker multiB = tickerRepository.save(new Ticker("MULTIB", "Multi B Co", "USD"));
        multiB.setRegularPaymentsPerYear(4);
        multiB = tickerRepository.save(multiB);
        seedYear(multiB, currentYear - 3, new BigDecimal("2.00"));
        seedYear(multiB, currentYear - 2, new BigDecimal("2.50"));
        // MULTIB는 가격 데이터 없음 — 배치 경로에서도 가격 미확보가 그대로 유지되는지 함께 확인

        // "정답"은 이미 다른 테스트들로 검증된 단건 경로(summarize)로 미리 구해둔다 —
        // 이 테스트의 목적은 계산식 자체가 아니라 summarizeAll()의 배치 조회가
        // summarize()와 동일한 결과를 내는지(리팩터링으로 값이 안 바뀌었는지) 확인하는 것.
        TickerSummary expectedA = service().summarize("MULTIA");
        TickerSummary expectedB = service().summarize("MULTIB");

        Map<String, TickerSummary> bySymbol = service().summarizeAll().stream()
                .collect(Collectors.toMap(TickerSummary::symbol, s -> s));

        assertEquals(expectedA, bySymbol.get("MULTIA"));
        assertEquals(expectedB, bySymbol.get("MULTIB"));
        assertTrue(bySymbol.get("MULTIB").currentPrice().isEmpty());
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
