package com.dividendanatomy.service.yield;

import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.yield.TtmDividendSummary;
import com.dividendanatomy.domain.yield.YieldContribution;

import java.time.LocalDate;
import java.util.Optional;

public record YieldDecompositionResult(
        String tickerSymbol,
        LocalDate t0,
        LocalDate t1,
        PriceBar priceAtT0,
        PriceBar priceAtT1,
        TtmDividendSummary ttmAtT0,
        TtmDividendSummary ttmAtT1,
        YieldContribution actual,
        Optional<YieldContribution> annualized) {

    /** 가장 가까운 값 조회로 실제 요청 날짜와 다른 날짜가 쓰였는지 — 화면의 "데이터 불완전" 판단 재료. */
    public boolean usedFallbackPriceAtT0() {
        return !priceAtT0.getDate().equals(t0);
    }

    public boolean usedFallbackPriceAtT1() {
        return !priceAtT1.getDate().equals(t1);
    }
}
