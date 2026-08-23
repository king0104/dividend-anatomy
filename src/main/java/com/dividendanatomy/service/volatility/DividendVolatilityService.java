package com.dividendanatomy.service.volatility;

import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.volatility.DividendVolatilityCalculator;
import com.dividendanatomy.domain.volatility.VolatilityResult;
import com.dividendanatomy.domain.yield.TtmDividendSummary;
import com.dividendanatomy.repository.TickerRepository;
import com.dividendanatomy.service.yield.TtmDividendAggregationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 티커 심볼 + 기준일(t1)을 받아 최근 10년 배당 증감률의 표본 표준편차를
 * 계산한다. DB만 읽는다 — 외부 API 호출 없음
 * (CLAUDE.md "서비스 계층은 DB만 읽는다").
 */
@Service
public class DividendVolatilityService {

    private static final int SAMPLE_YEARS = 10;

    private final TickerRepository tickerRepository;
    private final TtmDividendAggregationService ttmDividendAggregationService;

    public DividendVolatilityService(
            TickerRepository tickerRepository, TtmDividendAggregationService ttmDividendAggregationService) {
        this.tickerRepository = tickerRepository;
        this.ttmDividendAggregationService = ttmDividendAggregationService;
    }

    public VolatilityResult evaluate(String symbol, LocalDate asOf) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("알 수 없는 티커: " + symbol));

        Integer expectedCount = ticker.getRegularPaymentsPerYear();
        if (expectedCount == null) {
            throw new IllegalStateException(
                    "%s: 연간 정기 배당 지급 횟수가 설정되지 않음 (regularPaymentsPerYear)".formatted(symbol));
        }

        List<TtmDividendSummary> summaries = new ArrayList<>();
        for (int i = SAMPLE_YEARS; i >= 0; i--) {
            summaries.add(ttmDividendAggregationService.summarize(ticker, asOf.minusYears(i), expectedCount));
        }

        return DividendVolatilityCalculator.evaluate(summaries);
    }
}
