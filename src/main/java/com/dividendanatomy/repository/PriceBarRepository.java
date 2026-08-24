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

    /** 이 티커에 대해 실제로 확보된 가장 오래된 가격 — 타임머신 시뮬레이터가 요청 기간보다 짧은 실제 이력을 감지할 때 사용. */
    Optional<PriceBar> findTopByTickerOrderByDateAsc(Ticker ticker);

    /**
     * 여러 티커의 "이 날짜 이전·근처 가장 가까운 값"을 한 번에 조회 (N+1 방지).
     * 상관 서브쿼리(행마다 재실행)로 처음 시도했다가 운영 DB에서 응답이
     * 44초까지 튀는 걸 실측하고 되돌린 적 있음 — 대신 "티커별 최신 날짜"를
     * GROUP BY로 먼저 구한 뒤 JOIN하는 방식을 쓴다. MySQL이 GROUP BY는
     * (ticker_id, date) 유니크 인덱스로 잘 최적화하지만, 행 단위 상관
     * 서브쿼리는 그렇지 않았다.
     */
    @Query(
            value = """
                    SELECT p.* FROM price_bar p
                    INNER JOIN (
                        SELECT ticker_id, MAX(date) AS max_date
                        FROM price_bar
                        WHERE ticker_id IN (:tickerIds) AND date <= :onOrBefore
                        GROUP BY ticker_id
                    ) latest ON p.ticker_id = latest.ticker_id AND p.date = latest.max_date
                    """,
            nativeQuery = true)
    List<PriceBar> findLatestOnOrBeforeForTickerIds(@Param("tickerIds") List<Long> tickerIds, @Param("onOrBefore") LocalDate onOrBefore);
}
