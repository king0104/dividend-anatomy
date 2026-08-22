package com.dividendanatomy.repository;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DividendPaymentRepository extends JpaRepository<DividendPayment, Long> {

    /** Between은 Spring Data에서 양 끝 포함 — TTM 창 "[t-12개월, t] 양 끝 포함" 정의와 그대로 맞는다. */
    List<DividendPayment> findByTickerAndTypeAndExDividendDateBetweenOrderByExDividendDateAsc(
            Ticker ticker, DividendType type, LocalDate start, LocalDate end);
}
