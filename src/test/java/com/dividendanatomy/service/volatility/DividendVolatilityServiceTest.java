package com.dividendanatomy.service.volatility;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.volatility.DividendVolatilityCalculator;
import com.dividendanatomy.domain.volatility.VolatilityResult;
import com.dividendanatomy.domain.volatility.VolatilityStatus;
import com.dividendanatomy.domain.yield.TtmDividendSummary;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.SplitEventRepository;
import com.dividendanatomy.repository.TickerRepository;
import com.dividendanatomy.service.yield.TtmDividendAggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class DividendVolatilityServiceTest {

    private static final int SAMPLE_YEARS = 10;

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;

    @Autowired
    private SplitEventRepository splitEventRepository;

    private TtmDividendAggregationService ttmDividendAggregationService() {
        return new TtmDividendAggregationService(dividendPaymentRepository, splitEventRepository);
    }

    private DividendVolatilityService service() {
        return new DividendVolatilityService(tickerRepository, ttmDividendAggregationService());
    }

    /**
     * 1년 간격 클러스터(4분기 지급) 11개를 서로 안 겹치게 심고, 서비스 결과를
     * 테스트 안에서 동일한 11개 summarize() 호출 + 순수 계산기로 직접
     * 재계산한 값과 대조한다(배선 검증 — 통계 공식 자체는
     * DividendVolatilityCalculatorTest에서 이미 손계산으로 검증함).
     */
    @Test
    void wiresElevenYearlyTtmPointsIntoCalculator() {
        Ticker ticker = new Ticker("VOLCO", "Volatility Co", "USD");
        ticker.setRegularPaymentsPerYear(4);
        ticker = tickerRepository.save(ticker);

        LocalDate t1 = LocalDate.parse("2026-08-22");
        BigDecimal amount = new BigDecimal("0.25");
        for (int yearsAgo = 0; yearsAgo <= SAMPLE_YEARS; yearsAgo++) {
            LocalDate anchor = t1.minusYears(yearsAgo);
            seedQuarterlyCluster(ticker, anchor, amount);
            amount = amount.multiply(new BigDecimal("1.03"));
        }

        VolatilityResult result = service().evaluate("VOLCO", t1);

        List<TtmDividendSummary> expectedSummaries = new ArrayList<>();
        Ticker finalTicker = ticker;
        for (int i = SAMPLE_YEARS; i >= 0; i--) {
            expectedSummaries.add(ttmDividendAggregationService().summarize(finalTicker, t1.minusYears(i), 4));
        }
        VolatilityResult expected = DividendVolatilityCalculator.evaluate(expectedSummaries);

        assertEquals(expected.status(), result.status());
        assertEquals(0, expected.meanGrowthRate().get().compareTo(result.meanGrowthRate().get()));
        assertEquals(0, expected.standardDeviation().get().compareTo(result.standardDeviation().get()));
    }

    @Test
    void returnsInsufficientDataWhenTenYearHistoryIsMissing() {
        Ticker ticker = new Ticker("YOUNG2", "Young Co 2", "USD");
        ticker.setRegularPaymentsPerYear(4);
        ticker = tickerRepository.save(ticker);

        LocalDate t1 = LocalDate.parse("2026-08-22");
        seedQuarterlyCluster(ticker, t1, new BigDecimal("0.25"));
        seedQuarterlyCluster(ticker, t1.minusYears(1), new BigDecimal("0.24"));
        // 나머지 9개 연도 클러스터 없음 — 상장 2년 차

        VolatilityResult result = service().evaluate("YOUNG2", t1);

        assertEquals(VolatilityStatus.INSUFFICIENT_DATA, result.status());
    }

    @Test
    void throwsWhenRegularPaymentsPerYearIsNotSet() {
        tickerRepository.save(new Ticker("NOFREQ2", "No Frequency Co 2", "USD"));

        assertThrows(IllegalStateException.class,
                () -> service().evaluate("NOFREQ2", LocalDate.parse("2026-08-22")));
    }

    @Test
    void throwsWhenTickerUnknown() {
        assertThrows(NoSuchElementException.class,
                () -> service().evaluate("NOPE2", LocalDate.parse("2026-08-22")));
    }

    private void seedQuarterlyCluster(Ticker ticker, LocalDate anchor, BigDecimal amount) {
        saveDividend(ticker, anchor.minusMonths(9), amount);
        saveDividend(ticker, anchor.minusMonths(6), amount);
        saveDividend(ticker, anchor.minusMonths(3), amount);
        saveDividend(ticker, anchor, amount);
    }

    private void saveDividend(Ticker ticker, LocalDate date, BigDecimal amount) {
        dividendPaymentRepository.save(
                new DividendPayment(ticker, date, date, date, amount, DividendType.REGULAR, DataSource.MASSIVE));
    }
}
