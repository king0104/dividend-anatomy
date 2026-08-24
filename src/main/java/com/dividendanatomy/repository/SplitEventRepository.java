package com.dividendanatomy.repository;

import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SplitEventRepository extends JpaRepository<SplitEvent, Long> {

    /** 특정 배당 이후 발생한 분할들 — TTM 집계 시 raw 배당을 현재 주식 수 기준으로 환산할 때 사용. */
    List<SplitEvent> findByTickerAndExecutionDateAfterOrderByExecutionDateAsc(Ticker ticker, LocalDate date);

    /** 종목의 전체 분할 이력을 한 번에 가져와 배당 건별로 메모리에서 필터링할 때 사용(N+1 방지). */
    List<SplitEvent> findByTickerOrderByExecutionDateAsc(Ticker ticker);

    /** 여러 티커의 분할 이력을 한 번에 조회 — 목록 화면에서 티커마다 반복 조회하는 대신 사용(N+1 방지). */
    List<SplitEvent> findByTickerInOrderByExecutionDateAsc(List<Ticker> tickers);

    Optional<SplitEvent> findByTickerAndExecutionDate(Ticker ticker, LocalDate executionDate);
}
