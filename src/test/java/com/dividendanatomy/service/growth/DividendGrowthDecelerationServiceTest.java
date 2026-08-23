package com.dividendanatomy.service.growth;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.growth.GrowthDecelerationResult;
import com.dividendanatomy.domain.growth.GrowthDecelerationStatus;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.math.NthRootCalculator;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.SplitEventRepository;
import com.dividendanatomy.repository.TickerRepository;
import com.dividendanatomy.service.yield.TtmDividendAggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class DividendGrowthDecelerationServiceTest {

    private static final MathContext MC = MathContext.DECIMAL64;

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;

    @Autowired
    private SplitEventRepository splitEventRepository;

    private DividendGrowthDecelerationService service() {
        TtmDividendAggregationService ttm = new TtmDividendAggregationService(dividendPaymentRepository, splitEventRepository);
        return new DividendGrowthDecelerationService(tickerRepository, ttm);
    }

    /**
     * 세 시점의 TTM을 서로 안 겹치는 "클러스터"(연도별 분기 지급 4건)로 구성:
     * t1-10년=1.00, t1-3년=1.50, t1=1.60. 장기(10년)엔 빠르게 늘었는데
     * 최근 3년은 느려졌으니 둔화여야 한다 — 순수 계산기와 동일한 값으로 검증.
     */
    @Test
    void detectsDecelerationAcrossIsolatedTtmWindows() {
        Ticker ticker = new Ticker("GROW", "Growth Co", "USD");
        ticker.setRegularPaymentsPerYear(4);
        ticker = tickerRepository.save(ticker);

        LocalDate t1 = LocalDate.parse("2026-08-22");
        LocalDate t1Minus3 = t1.minusYears(3);
        LocalDate t1Minus10 = t1.minusYears(10);

        seedQuarterlyCluster(ticker, t1, "0.40");
        seedQuarterlyCluster(ticker, t1Minus3, "0.375");
        seedQuarterlyCluster(ticker, t1Minus10, "0.25");

        GrowthDecelerationResult result = service().evaluate("GROW", t1);

        BigDecimal expectedShort = NthRootCalculator.nthRoot(new BigDecimal("1.60").divide(new BigDecimal("1.50"), MC), 3, MC)
                .subtract(BigDecimal.ONE, MC);
        BigDecimal expectedLong = NthRootCalculator.nthRoot(new BigDecimal("1.60").divide(new BigDecimal("1.00"), MC), 10, MC)
                .subtract(BigDecimal.ONE, MC);

        assertEquals(GrowthDecelerationStatus.DECELERATING, result.status());
        assertTrue(result.cagrShort().isPresent());
        assertTrue(result.cagrLong().isPresent());
        assertEquals(0, expectedShort.compareTo(result.cagrShort().get()));
        assertEquals(0, expectedLong.compareTo(result.cagrLong().get()));
        assertTrue(result.decelerationGap().isPresent());
    }

    @Test
    void returnsInsufficientDataWhenTenYearHistoryIsMissing() {
        Ticker ticker = new Ticker("YOUNG", "Young Co", "USD");
        ticker.setRegularPaymentsPerYear(4);
        ticker = tickerRepository.save(ticker);

        LocalDate t1 = LocalDate.parse("2026-08-22");
        seedQuarterlyCluster(ticker, t1, "0.40");
        seedQuarterlyCluster(ticker, t1.minusYears(3), "0.375");
        // t1-10년 클러스터 없음 — 상장 10년 미만

        GrowthDecelerationResult result = service().evaluate("YOUNG", t1);

        assertEquals(GrowthDecelerationStatus.INSUFFICIENT_DATA, result.status());
        assertTrue(result.cagrLong().isEmpty());
    }

    @Test
    void throwsWhenRegularPaymentsPerYearIsNotSet() {
        tickerRepository.save(new Ticker("NOFREQ", "No Frequency Co", "USD"));

        assertThrows(IllegalStateException.class,
                () -> service().evaluate("NOFREQ", LocalDate.parse("2026-08-22")));
    }

    @Test
    void throwsWhenTickerUnknown() {
        assertThrows(NoSuchElementException.class,
                () -> service().evaluate("NOPE", LocalDate.parse("2026-08-22")));
    }

    /** anchor를 끝으로 하는 12개월 창 안에만 들어가는 분기 지급 4건 (다른 클러스터와 절대 안 겹치게 몇 년 단위로 떨어뜨려 씀). */
    private void seedQuarterlyCluster(Ticker ticker, LocalDate anchor, String amount) {
        saveDividend(ticker, anchor.minusMonths(9), amount);
        saveDividend(ticker, anchor.minusMonths(6), amount);
        saveDividend(ticker, anchor.minusMonths(3), amount);
        saveDividend(ticker, anchor, amount);
    }

    private void saveDividend(Ticker ticker, LocalDate date, String amount) {
        dividendPaymentRepository.save(
                new DividendPayment(ticker, date, date, date, new BigDecimal(amount), DividendType.REGULAR, DataSource.MASSIVE));
    }
}
