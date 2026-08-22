package com.dividendanatomy.service.yield;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.yield.TtmDividendSummary;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.SplitEventRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest
class TtmDividendAggregationServiceTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;

    @Autowired
    private SplitEventRepository splitEventRepository;

    private TtmDividendAggregationService service() {
        return new TtmDividendAggregationService(dividendPaymentRepository, splitEventRepository);
    }

    private void saveDividend(Ticker ticker, String exDividendDate, String amount, DividendType type) {
        LocalDate d = LocalDate.parse(exDividendDate);
        dividendPaymentRepository.save(new DividendPayment(ticker, d, d, d, new BigDecimal(amount), type, DataSource.MASSIVE));
    }

    /**
     * 실제 KO 분할 이력 재사용 (docs/decisions/03-split-adjustment.md).
     * 분할 이전 배당(0.51)은 그 이후 2:1 분할이 있었으므로 0.255로 조정돼야
     * 정상 — 조정 안 하면 합계가 1.53이 나온다.
     */
    @Test
    void adjustsPreSplitDividendsToCurrentShareBasis() {
        Ticker ko = tickerRepository.save(new Ticker("KO", "The Coca-Cola Company", "USD"));
        splitEventRepository.save(new SplitEvent(ko, LocalDate.parse("2012-08-13"),
                new BigDecimal("2"), DataSource.MASSIVE));

        saveDividend(ko, "2012-03-13", "0.51", DividendType.REGULAR);
        saveDividend(ko, "2012-06-13", "0.51", DividendType.REGULAR);
        saveDividend(ko, "2012-09-12", "0.255", DividendType.REGULAR);
        saveDividend(ko, "2012-11-28", "0.255", DividendType.REGULAR);

        TtmDividendSummary summary = service().summarize(ko, LocalDate.parse("2012-12-31"), 4);

        assertEquals(4, summary.foundCount());
        assertEquals(0, new BigDecimal("1.02").compareTo(summary.actualSum()));
    }

    @Test
    void noSplitMeansRawSumUnchanged() {
        Ticker pep = tickerRepository.save(new Ticker("PEP", "PepsiCo", "USD"));
        saveDividend(pep, "2025-09-01", "1.00", DividendType.REGULAR);
        saveDividend(pep, "2025-12-01", "1.00", DividendType.REGULAR);
        saveDividend(pep, "2026-03-01", "1.00", DividendType.REGULAR);
        saveDividend(pep, "2026-06-01", "1.00", DividendType.REGULAR);

        TtmDividendSummary summary = service().summarize(pep, LocalDate.parse("2026-08-22"), 4);

        assertEquals(4, summary.foundCount());
        assertEquals(0, new BigDecimal("4.00").compareTo(summary.actualSum()));
    }

    @Test
    void annualizesWhenWindowHasGap() {
        Ticker jnj = tickerRepository.save(new Ticker("JNJ", "Johnson & Johnson", "USD"));
        saveDividend(jnj, "2025-12-01", "1.00", DividendType.REGULAR);
        saveDividend(jnj, "2026-03-01", "1.00", DividendType.REGULAR);
        saveDividend(jnj, "2026-06-01", "1.00", DividendType.REGULAR);
        // 4번째 분기 배당은 창 안에 없음 (구멍)

        TtmDividendSummary summary = service().summarize(jnj, LocalDate.parse("2026-08-22"), 4);

        assertEquals(3, summary.foundCount());
        assertEquals(0, new BigDecimal("3.00").compareTo(summary.actualSum()));
        // annualizedSum = actualSum * expectedCount / foundCount = 3.00 * 4 / 3 = 4.00
        assertEquals(0, new BigDecimal("4.00").compareTo(summary.annualizedSum()));
    }

    @Test
    void noDividendsInWindowMeansAnnualizedIsNull() {
        Ticker newco = tickerRepository.save(new Ticker("NEWCO", "New Company", "USD"));

        TtmDividendSummary summary = service().summarize(newco, LocalDate.parse("2026-08-22"), 4);

        assertEquals(0, summary.foundCount());
        assertNull(summary.annualizedSum());
    }
}
