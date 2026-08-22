package com.dividendanatomy.repository;

import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface PriceBarRepository extends JpaRepository<PriceBar, Long> {

    /**
     * "가장 가까운 값" 조회 원칙 (CLAUDE.md) — 정확히 이 날짜가 아니라
     * 이 날짜 이전·근처의 가장 최근값을 찾는다.
     */
    Optional<PriceBar> findTopByTickerAndDateLessThanEqualOrderByDateDesc(Ticker ticker, LocalDate date);
}
