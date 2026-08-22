package com.dividendanatomy.service.yield;

import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.yield.TtmDividendSummary;
import com.dividendanatomy.domain.yield.YieldChangeDecomposer;
import com.dividendanatomy.domain.yield.YieldContribution;
import com.dividendanatomy.repository.PriceBarRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 티커 심볼 + 기준일(t1)을 받아 롤링 1년 전(t0) 대비 배당수익률 변화
 * 기여도 분해를 계산한다. DB만 읽는다 — 외부 API 호출 없음
 * (CLAUDE.md "서비스 계층은 DB만 읽는다").
 */
@Service
public class YieldDecompositionService {

    private final TickerRepository tickerRepository;
    private final PriceBarRepository priceBarRepository;
    private final TtmDividendAggregationService ttmDividendAggregationService;

    public YieldDecompositionService(
            TickerRepository tickerRepository,
            PriceBarRepository priceBarRepository,
            TtmDividendAggregationService ttmDividendAggregationService) {
        this.tickerRepository = tickerRepository;
        this.priceBarRepository = priceBarRepository;
        this.ttmDividendAggregationService = ttmDividendAggregationService;
    }

    public YieldDecompositionResult decompose(String symbol, LocalDate t1) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("알 수 없는 티커: " + symbol));

        Integer expectedCount = ticker.getRegularPaymentsPerYear();
        if (expectedCount == null) {
            throw new IllegalStateException(
                    "%s: 연간 정기 배당 지급 횟수가 설정되지 않음 (regularPaymentsPerYear)".formatted(symbol));
        }

        LocalDate t0 = t1.minusYears(1);

        PriceBar priceBar1 = requirePriceOnOrBefore(ticker, t1);
        PriceBar priceBar0 = requirePriceOnOrBefore(ticker, t0);

        TtmDividendSummary ttm0 = ttmDividendAggregationService.summarize(ticker, t0, expectedCount);
        TtmDividendSummary ttm1 = ttmDividendAggregationService.summarize(ticker, t1, expectedCount);

        YieldContribution actual = YieldChangeDecomposer.decompose(
                ttm0.actualSum(), priceBar0.getClose(), ttm1.actualSum(), priceBar1.getClose());
        Optional<YieldContribution> annualized = YieldChangeDecomposer.decomposeAnnualized(
                ttm0, ttm1, priceBar0.getClose(), priceBar1.getClose());

        return new YieldDecompositionResult(
                ticker.getSymbol(), t0, t1, priceBar0, priceBar1, ttm0, ttm1, actual, annualized);
    }

    private PriceBar requirePriceOnOrBefore(Ticker ticker, LocalDate date) {
        return priceBarRepository.findTopByTickerAndDateLessThanEqualOrderByDateDesc(ticker, date)
                .orElseThrow(() -> new NoSuchElementException(
                        "%s: %s 이전 가격 데이터 없음".formatted(ticker.getSymbol(), date)));
    }
}
