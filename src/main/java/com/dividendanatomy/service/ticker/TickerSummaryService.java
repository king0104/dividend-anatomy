package com.dividendanatomy.service.ticker;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.split.SplitAdjustmentCalculator;
import com.dividendanatomy.domain.ticker.CurrentYieldResult;
import com.dividendanatomy.domain.ticker.DividendIncreaseStreakCalculator;
import com.dividendanatomy.domain.ticker.DividendIncreaseStreakResult;
import com.dividendanatomy.domain.ticker.TickerSummary;
import com.dividendanatomy.domain.yield.TtmDividendSummary;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.PriceBarRepository;
import com.dividendanatomy.repository.SplitEventRepository;
import com.dividendanatomy.repository.TickerRepository;
import com.dividendanatomy.service.yield.TtmDividendAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 종목 목록 화면(PROJECT.md 4.1절)에 필요한 요약 지표 — 현재
 * 시가배당률과 연속 배당 증가 연수 — 를 계산한다. DB만 읽는다
 * (CLAUDE.md "서비스 계층은 DB만 읽는다").
 */
@Service
public class TickerSummaryService {

    private static final Logger log = LoggerFactory.getLogger(TickerSummaryService.class);

    private final TickerRepository tickerRepository;
    private final DividendPaymentRepository dividendPaymentRepository;
    private final PriceBarRepository priceBarRepository;
    private final SplitEventRepository splitEventRepository;
    private final TtmDividendAggregationService ttmDividendAggregationService;

    public TickerSummaryService(
            TickerRepository tickerRepository,
            DividendPaymentRepository dividendPaymentRepository,
            PriceBarRepository priceBarRepository,
            SplitEventRepository splitEventRepository,
            TtmDividendAggregationService ttmDividendAggregationService) {
        this.tickerRepository = tickerRepository;
        this.dividendPaymentRepository = dividendPaymentRepository;
        this.priceBarRepository = priceBarRepository;
        this.splitEventRepository = splitEventRepository;
        this.ttmDividendAggregationService = ttmDividendAggregationService;
    }

    public TickerSummary summarize(String symbol) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("알 수 없는 티커: " + symbol));
        Integer expectedCount = ticker.getRegularPaymentsPerYear();
        if (expectedCount == null) {
            throw new IllegalStateException(
                    "%s: 연간 정기 배당 지급 횟수가 설정되지 않음 (regularPaymentsPerYear)".formatted(symbol));
        }

        LocalDate today = LocalDate.now();

        TtmDividendSummary ttm = ttmDividendAggregationService.summarize(ticker, today, expectedCount);
        Optional<BigDecimal> price = priceBarRepository
                .findTopByTickerAndDateLessThanEqualOrderByDateDesc(ticker, today)
                .map(PriceBar::getClose);
        CurrentYieldResult yieldResult = CurrentYieldResult.from(ttm, price);

        DividendIncreaseStreakResult streakResult = calculateStreak(ticker, expectedCount, today.getYear());

        return new TickerSummary(ticker.getSymbol(), ticker.getName(), ticker.getCurrency(), price, expectedCount, yieldResult, streakResult);
    }

    public List<TickerSummary> summarizeAll() {
        return tickerRepository.findAll().stream()
                .map(t -> {
                    try {
                        return Optional.of(summarize(t.getSymbol()));
                    } catch (RuntimeException e) {
                        log.warn("종목 요약 계산 실패, 목록에서 제외: ticker={} reason={}", t.getSymbol(), e.getMessage());
                        return Optional.<TickerSummary>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private DividendIncreaseStreakResult calculateStreak(Ticker ticker, int expectedCount, int currentYear) {
        List<DividendPayment> regularPayments =
                dividendPaymentRepository.findByTickerAndTypeOrderByExDividendDateAsc(ticker, DividendType.REGULAR);

        Map<Integer, BigDecimal> annualTotalsByYear = new HashMap<>();
        Map<Integer, Integer> paymentCountByYear = new HashMap<>();
        for (DividendPayment payment : regularPayments) {
            int year = payment.getExDividendDate().getYear();
            List<SplitEvent> laterSplits = splitEventRepository
                    .findByTickerAndExecutionDateAfterOrderByExecutionDateAsc(ticker, payment.getExDividendDate());
            BigDecimal adjusted = SplitAdjustmentCalculator.adjustedAmount(laterSplits, payment.getAmount());

            annualTotalsByYear.merge(year, adjusted, BigDecimal::add);
            paymentCountByYear.merge(year, 1, Integer::sum);
        }

        return DividendIncreaseStreakCalculator.evaluate(annualTotalsByYear, paymentCountByYear, expectedCount, currentYear);
    }
}
