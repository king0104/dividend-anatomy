package com.dividendanatomy.repository;

import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceBarRepository extends JpaRepository<PriceBar, Long> {

    /**
     * "가장 가까운 값" 조회 원칙 (CLAUDE.md) — 정확히 이 날짜가 아니라
     * 이 날짜 이전·근처의 가장 최근값을 찾는다.
     */
    Optional<PriceBar> findTopByTickerAndDateLessThanEqualOrderByDateDesc(Ticker ticker, LocalDate date);

    Optional<PriceBar> findByTickerAndDate(Ticker ticker, LocalDate date);

    /**
     * 여러 티커의 "이 날짜 이전·근처 가장 가까운 값"을 한 번에 조회 (N+1 방지).
     * 티커별로 findTopByTickerAndDateLessThanEqualOrderByDateDesc를 반복 호출하는 대신,
     * 상관 서브쿼리로 티커당 정확히 1행(있다면)만 반환한다.
     */
    @Query("""
            SELECT p FROM PriceBar p
            WHERE p.ticker IN :tickers
              AND p.date = (
                SELECT MAX(p2.date) FROM PriceBar p2
                WHERE p2.ticker = p.ticker AND p2.date <= :onOrBefore
              )
            """)
    List<PriceBar> findLatestOnOrBeforeForTickers(@Param("tickers") List<Ticker> tickers, @Param("onOrBefore") LocalDate onOrBefore);
}
