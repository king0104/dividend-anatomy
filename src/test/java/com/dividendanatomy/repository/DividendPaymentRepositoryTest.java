package com.dividendanatomy.repository;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class DividendPaymentRepositoryTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;

    private Ticker ko;

    private void seed() {
        ko = tickerRepository.save(new Ticker("KO", "The Coca-Cola Company", "USD"));

        // 창의 시작 경계(제외 대상), 창 중간, 창의 끝 경계(포함) — 시작점 제외/끝만 포함 확인
        // (docs/decisions/05-ttm-window-boundary-fix.md)
        save(ko, "2025-08-22", "0.51", DividendType.REGULAR); // 시작 경계 — 이제 제외돼야 함
        save(ko, "2025-11-14", "0.51", DividendType.REGULAR);
        save(ko, "2026-08-22", "0.99", DividendType.SPECIAL); // 끝 경계 안에 있지만 타입 필터로 제외돼야 함
        // 창 밖 (하루 전)
        save(ko, "2025-08-21", "0.51", DividendType.REGULAR);
    }

    private void save(Ticker ticker, String exDividendDate, String amount, DividendType type) {
        LocalDate d = LocalDate.parse(exDividendDate);
        dividendPaymentRepository.save(new DividendPayment(ticker, d, d, d, new BigDecimal(amount), type, DataSource.MASSIVE));
    }

    @Test
    void startBoundaryIsExcludedEndBoundaryIsIncludedAndTypeIsFiltered() {
        seed();

        List<DividendPayment> result = dividendPaymentRepository
                .findByTickerAndTypeAndExDividendDateAfterAndExDividendDateLessThanEqualOrderByExDividendDateAsc(
                        ko, DividendType.REGULAR, LocalDate.parse("2025-08-22"), LocalDate.parse("2026-08-22"));

        assertEquals(1, result.size());
        assertEquals(LocalDate.parse("2025-11-14"), result.get(0).getExDividendDate());
        assertTrue(result.stream().allMatch(p -> p.getType() == DividendType.REGULAR));
    }

    @Test
    void excludesEntriesOutsideWindow() {
        seed();

        List<DividendPayment> result = dividendPaymentRepository
                .findByTickerAndTypeAndExDividendDateAfterAndExDividendDateLessThanEqualOrderByExDividendDateAsc(
                        ko, DividendType.REGULAR, LocalDate.parse("2025-08-22"), LocalDate.parse("2026-08-22"));

        assertTrue(result.stream().noneMatch(p -> p.getExDividendDate().equals(LocalDate.parse("2025-08-21"))));
    }

    @Test
    void findByTickerInReturnsEachTickersOwnPaymentsWithoutCrossContamination() {
        seed(); // ko: REGULAR 3건 + SPECIAL 1건
        Ticker pep = tickerRepository.save(new Ticker("PEP", "PepsiCo", "USD"));
        save(pep, "2025-09-01", "1.00", DividendType.REGULAR);

        List<DividendPayment> result = dividendPaymentRepository
                .findByTickerInAndTypeOrderByExDividendDateAsc(List.of(ko, pep), DividendType.REGULAR);

        assertEquals(4, result.size()); // ko REGULAR 3건 + pep REGULAR 1건 (SPECIAL은 타입 필터로 제외)
        assertTrue(result.stream().filter(p -> p.getTicker().equals(ko)).allMatch(p -> p.getType() == DividendType.REGULAR));
        assertEquals(1, result.stream().filter(p -> p.getTicker().equals(pep)).count());
    }
}
