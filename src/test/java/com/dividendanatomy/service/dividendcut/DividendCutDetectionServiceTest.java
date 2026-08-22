package com.dividendanatomy.service.dividendcut;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.dividendcut.CutComparisonResult;
import com.dividendanatomy.domain.dividendcut.CutComparisonStatus;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.DividendPaymentRepository;
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
class DividendCutDetectionServiceTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;

    @Autowired
    private SplitEventRepository splitEventRepository;

    private DividendCutDetectionService service() {
        TtmDividendAggregationService ttm =
                new TtmDividendAggregationService(dividendPaymentRepository, splitEventRepository);
        return new DividendCutDetectionService(tickerRepository, dividendPaymentRepository, ttm);
    }

    /**
     * raw 금액만 보면 분할 직후 지급액이 절반(1.00→0.50)이 돼서 삭감처럼 보이지만,
     * 분할 조정(TtmDividendAggregationService가 이미 처리)을 거치면 TTM 합계가
     * 그대로(2.00→2.00)라 삭감이 아니어야 한다 (예측 #10을 정면으로 겨냥).
     * 지급일 간격을 "3개월+1일"로 둬서 4번째 이전 지급이 TTM 창(12개월) 경계에
     * 걸쳐 이중 계산되는 걸 피한다 (docs/ai-defects/04-ttm-window-boundary-overlap.md).
     */
    @Test
    void doesNotFalselyFlagCutAcrossStockSplit() {
        Ticker ticker = new Ticker("SPLITCUT", "Split Cut Co", "USD");
        ticker.setRegularPaymentsPerYear(4);
        ticker = tickerRepository.save(ticker);

        LocalDate p1 = LocalDate.parse("2024-01-02");
        LocalDate p2 = p1.plusMonths(3).plusDays(1);
        LocalDate p3 = p2.plusMonths(3).plusDays(1);
        LocalDate p4 = p3.plusMonths(3).plusDays(1);
        LocalDate p5 = p4.plusMonths(3).plusDays(1);
        LocalDate p6 = p5.plusMonths(3).plusDays(1);
        LocalDate p7 = p6.plusMonths(3).plusDays(1);
        LocalDate p8 = p7.plusMonths(3).plusDays(1);

        saveDividend(ticker, p1, "1.00");
        saveDividend(ticker, p2, "1.00");
        saveDividend(ticker, p3, "1.00");
        saveDividend(ticker, p4, "1.00");
        splitEventRepository.save(new SplitEvent(ticker, p4.plusDays(15), new BigDecimal("2"), DataSource.MASSIVE));
        saveDividend(ticker, p5, "0.50");
        saveDividend(ticker, p6, "0.50");
        saveDividend(ticker, p7, "0.50");
        saveDividend(ticker, p8, "0.50");

        List<CutComparisonResult> results = service().detectCuts("SPLITCUT");

        assertEquals(7, results.size());
        assertTrue(results.stream().noneMatch(r -> r.status() == CutComparisonStatus.CUT),
                "분할 조정 후에는 어떤 구간도 CUT으로 판정되면 안 된다");

        // p4(2.00) vs p5(2.00) — raw로는 4.00 vs 3.50이라 감소처럼 보이지만 조정 후엔 동일.
        CutComparisonResult acrossSplit = results.get(3);
        assertEquals(p5, acrossSplit.detectedAt());
        assertEquals(CutComparisonStatus.NORMAL, acrossSplit.status());
        assertEquals(0, new BigDecimal("2.00").compareTo(acrossSplit.previousTtmAmount()));
        assertEquals(0, new BigDecimal("2.00").compareTo(acrossSplit.currentTtmAmount()));
    }

    @Test
    void throwsWhenRegularPaymentsPerYearIsNotSet() {
        tickerRepository.save(new Ticker("NOFREQ", "No Frequency Co", "USD"));

        assertThrows(IllegalStateException.class, () -> service().detectCuts("NOFREQ"));
    }

    @Test
    void throwsWhenTickerUnknown() {
        assertThrows(NoSuchElementException.class, () -> service().detectCuts("NOPE"));
    }

    @Test
    void returnsEmptyWhenOnlyOneRegularPaymentExists() {
        Ticker ticker = new Ticker("SOLO", "Solo Payment Co", "USD");
        ticker.setRegularPaymentsPerYear(4);
        ticker = tickerRepository.save(ticker);
        saveDividend(ticker, LocalDate.parse("2025-01-01"), "1.00");

        assertTrue(service().detectCuts("SOLO").isEmpty());
    }

    private void saveDividend(Ticker ticker, LocalDate date, String amount) {
        dividendPaymentRepository.save(
                new DividendPayment(ticker, date, date, date, new BigDecimal(amount), DividendType.REGULAR, DataSource.MASSIVE));
    }
}
