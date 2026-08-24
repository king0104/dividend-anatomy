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

    Optional<PriceBar> findByTickerAndDate(Ticker ticker, LocalDate date);

    /** 이 티커에 대해 실제로 확보된 가장 오래된 가격 — 타임머신 시뮬레이터가 요청 기간보다 짧은 실제 이력을 감지할 때 사용. */
    Optional<PriceBar> findTopByTickerOrderByDateAsc(Ticker ticker);
}
