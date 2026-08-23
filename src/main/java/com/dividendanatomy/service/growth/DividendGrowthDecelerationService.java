package com.dividendanatomy.service.growth;

import com.dividendanatomy.domain.growth.DividendGrowthCalculator;
import com.dividendanatomy.domain.growth.GrowthDecelerationResult;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.yield.TtmDividendSummary;
import com.dividendanatomy.repository.TickerRepository;
import com.dividendanatomy.service.yield.TtmDividendAggregationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.NoSuchElementException;

/**
 * 티커 심볼 + 기준일(t1)을 받아 최근 3년 CAGR과 10년 CAGR을 비교해
 * 배당 성장 둔화 여부를 계산한다. DB만 읽는다 — 외부 API 호출 없음
 * (CLAUDE.md "서비스 계층은 DB만 읽는다").
 */
@Service
public class DividendGrowthDecelerationService {

    private final TickerRepository tickerRepository;
    private final TtmDividendAggregationService ttmDividendAggregationService;

    public DividendGrowthDecelerationService(
            TickerRepository tickerRepository, TtmDividendAggregationService ttmDividendAggregationService) {
        this.tickerRepository = tickerRepository;
        this.ttmDividendAggregationService = ttmDividendAggregationService;
    }

    public GrowthDecelerationResult evaluate(String symbol, LocalDate asOf) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("알 수 없는 티커: " + symbol));

        Integer expectedCount = ticker.getRegularPaymentsPerYear();
        if (expectedCount == null) {
            throw new IllegalStateException(
                    "%s: 연간 정기 배당 지급 횟수가 설정되지 않음 (regularPaymentsPerYear)".formatted(symbol));
        }

        TtmDividendSummary t1 = ttmDividendAggregationService.summarize(ticker, asOf, expectedCount);
        TtmDividendSummary t1Minus3 = ttmDividendAggregationService.summarize(ticker, asOf.minusYears(3), expectedCount);
        TtmDividendSummary t1Minus10 = ttmDividendAggregationService.summarize(ticker, asOf.minusYears(10), expectedCount);

        return DividendGrowthCalculator.evaluate(t1, t1Minus3, t1Minus10);
    }
}
