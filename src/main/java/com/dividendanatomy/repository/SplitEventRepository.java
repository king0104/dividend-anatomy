package com.dividendanatomy.repository;

import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SplitEventRepository extends JpaRepository<SplitEvent, Long> {

    /** 특정 배당 이후 발생한 분할들 — TTM 집계 시 raw 배당을 현재 주식 수 기준으로 환산할 때 사용. */
    List<SplitEvent> findByTickerAndExecutionDateAfterOrderByExecutionDateAsc(Ticker ticker, LocalDate date);
}
