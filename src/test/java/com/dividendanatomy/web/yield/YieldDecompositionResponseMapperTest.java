package com.dividendanatomy.web.yield;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.yield.TtmDividendSummary;
import com.dividendanatomy.domain.yield.YieldContribution;
import com.dividendanatomy.service.yield.YieldDecompositionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class YieldDecompositionResponseMapperTest {

    /**
     * YieldChangeDecomposerTest의 손계산 케이스(price=0.00825, dividend=0.00675)를
     * 재사용 — %p 변환 후 0.825%p는 HALF_UP 반올림 경계값이라 0.83으로,
     * 0.675%p는 0.68로 올라가는지 확인하는 의도적 케이스.
     */
    @Test
    void roundsRawRatioToPercentWithHalfUp() {
        Ticker ticker = new Ticker("TEST", "Test Co", "USD");
        LocalDate t1 = LocalDate.parse("2026-08-22");
        LocalDate t0 = t1.minusYears(1);
        PriceBar priceAtT0 = new PriceBar(ticker, t0, new BigDecimal("100.00"), DataSource.TWELVE_DATA);
        PriceBar priceAtT1 = new PriceBar(ticker, t1, new BigDecimal("80.00"), DataSource.TWELVE_DATA);
        TtmDividendSummary ttm0 = new TtmDividendSummary(new BigDecimal("3.00"), new BigDecimal("3.00"), 4, 4);
        TtmDividendSummary ttm1 = new TtmDividendSummary(new BigDecimal("3.60"), new BigDecimal("3.60"), 4, 4);
        YieldContribution actual = new YieldContribution(new BigDecimal("0.00825"), new BigDecimal("0.00675"));

        YieldDecompositionResult result = new YieldDecompositionResult(
                "TEST", t0, t1, priceAtT0, priceAtT1, ttm0, ttm1, actual, Optional.of(actual));

        YieldDecompositionResponse response = YieldDecompositionResponseMapper.toResponse(result);

        assertEquals(0, new BigDecimal("0.83").compareTo(response.actual().priceContributionPercent()));
        assertEquals(0, new BigDecimal("0.68").compareTo(response.actual().dividendContributionPercent()));
        assertEquals(0, new BigDecimal("0.83").compareTo(response.annualized().priceContributionPercent()));
    }

    @Test
    void annualizedIsNullWhenNotPresent() {
        Ticker ticker = new Ticker("TEST", "Test Co", "USD");
        LocalDate t1 = LocalDate.parse("2026-08-22");
        LocalDate t0 = t1.minusYears(1);
        PriceBar priceAtT0 = new PriceBar(ticker, t0, new BigDecimal("100.00"), DataSource.TWELVE_DATA);
        PriceBar priceAtT1 = new PriceBar(ticker, t1, new BigDecimal("80.00"), DataSource.TWELVE_DATA);
        TtmDividendSummary ttm0 = new TtmDividendSummary(BigDecimal.ZERO, null, 0, 4);
        TtmDividendSummary ttm1 = new TtmDividendSummary(new BigDecimal("3.60"), new BigDecimal("3.60"), 4, 4);
        YieldContribution actual = new YieldContribution(new BigDecimal("0.01"), new BigDecimal("0.02"));

        YieldDecompositionResult result = new YieldDecompositionResult(
                "TEST", t0, t1, priceAtT0, priceAtT1, ttm0, ttm1, actual, Optional.empty());

        YieldDecompositionResponse response = YieldDecompositionResponseMapper.toResponse(result);

        assertNull(response.annualized());
        assertEquals(false, response.dataQuality().ttmCompleteAtT0()); // foundCount=0 < expectedCount=4
        assertEquals(true, response.dataQuality().ttmCompleteAtT1()); // foundCount=4 == expectedCount=4
    }
}
