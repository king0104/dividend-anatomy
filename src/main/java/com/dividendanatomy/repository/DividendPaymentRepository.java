package com.dividendanatomy.repository;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DividendPaymentRepository extends JpaRepository<DividendPayment, Long> {

    /**
     * TTM 창 "(t-12개월, t] 시작점 제외, 끝만 포함" 정의(docs/decisions/05-ttm-window-boundary-fix.md).
     * 양 끝 포함으로 하면 정확히 12개월 차이 나는 두 지급일이 인접한 두 창에 모두
     * 걸려 이중 계산된다 — 실제 KO 배당 이력에서 발생 확인(docs/ai-defects/04).
     */
    List<DividendPayment> findByTickerAndTypeAndExDividendDateAfterAndExDividendDateLessThanEqualOrderByExDividendDateAsc(
            Ticker ticker, DividendType type, LocalDate startExclusive, LocalDate endInclusive);

    Optional<DividendPayment> findByTickerAndExDividendDate(Ticker ticker, LocalDate exDividendDate);

    List<DividendPayment> findByTickerAndTypeOrderByExDividendDateAsc(Ticker ticker, DividendType type);

    List<DividendPayment> findByTickerOrderByExDividendDateAsc(Ticker ticker);

    /** 여러 티커의 정기 배당 이력을 한 번에 조회 — 목록 화면에서 티커마다 반복 조회하는 대신 사용(N+1 방지). */
    List<DividendPayment> findByTickerInAndTypeOrderByExDividendDateAsc(List<Ticker> tickers, DividendType type);
}
