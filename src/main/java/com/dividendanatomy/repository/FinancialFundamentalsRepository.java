package com.dividendanatomy.repository;

import com.dividendanatomy.domain.fundamentals.FinancialFundamentals;
import com.dividendanatomy.domain.market.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialFundamentalsRepository extends JpaRepository<FinancialFundamentals, Long> {

    Optional<FinancialFundamentals> findByTicker(Ticker ticker);
}
