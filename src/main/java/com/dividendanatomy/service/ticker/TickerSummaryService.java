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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

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

    /**
     * summarize()를 티커마다 반복 호출하면 배당/분할/가격 조회가 티커 수만큼 반복된다(N+1).
     * 대신 세 레포지토리를 티커 목록 전체에 대해 한 번씩만 호출하고, 티커별로 메모리에서
     * 묶어서 처리한다. TTM 집계(ttmDividendAggregationService)는 다른 4개 서비스가 함께
     * 쓰는 공용 컴포넌트라 그대로 티커당 1회 호출한다 — 그 안의 쿼리까지 배치로 묶으려면
     * 그 서비스를 쓰는 다른 화면들까지 건드려야 해서 이번 범위 밖으로 남겨둔다.
     */
    public List<TickerSummary> summarizeAll() {
        List<Ticker> tickers = tickerRepository.findAll();
        if (tickers.isEmpty()) {
            return List.of();
        }

        Map<Long, List<DividendPayment>> paymentsByTickerId = dividendPaymentRepository
                .findByTickerInAndTypeOrderByExDividendDateAsc(tickers, DividendType.REGULAR).stream()
                .collect(Collectors.groupingBy(p -> p.getTicker().getId()));
        Map<Long, List<SplitEvent>> splitsByTickerId = splitEventRepository
                .findByTickerInOrderByExecutionDateAsc(tickers).stream()
                .collect(Collectors.groupingBy(s -> s.getTicker().getId()));
        LocalDate today = LocalDate.now();
        Map<Long, PriceBar> latestPriceByTickerId = priceBarRepository
                .findLatestOnOrBeforeForTickers(tickers, today).stream()
                .collect(Collectors.toMap(p -> p.getTicker().getId(), p -> p));

        List<TickerSummary> summaries = new ArrayList<>();
        for (Ticker ticker : tickers) {
            try {
                summaries.add(summarizeFromPrefetched(
                        ticker,
                        paymentsByTickerId.getOrDefault(ticker.getId(), List.of()),
                        splitsByTickerId.getOrDefault(ticker.getId(), List.of()),
                        Optional.ofNullable(latestPriceByTickerId.get(ticker.getId())),
                        today));
            } catch (RuntimeException e) {
                log.warn("종목 요약 계산 실패, 목록에서 제외: ticker={} reason={}", ticker.getSymbol(), e.getMessage());
            }
        }
        return summaries;
    }

    private TickerSummary summarizeFromPrefetched(
            Ticker ticker, List<DividendPayment> regularPayments, List<SplitEvent> allSplits,
            Optional<PriceBar> latestPriceBar, LocalDate today) {
        Integer expectedCount = ticker.getRegularPaymentsPerYear();
        if (expectedCount == null) {
            throw new IllegalStateException(
                    "%s: 연간 정기 배당 지급 횟수가 설정되지 않음 (regularPaymentsPerYear)".formatted(ticker.getSymbol()));
        }

        TtmDividendSummary ttm = ttmDividendAggregationService.summarize(ticker, today, expectedCount);
        Optional<BigDecimal> price = latestPriceBar.map(PriceBar::getClose);
        CurrentYieldResult yieldResult = CurrentYieldResult.from(ttm, price);

        DividendIncreaseStreakResult streakResult =
                calculateStreak(regularPayments, allSplits, expectedCount, today.getYear());

        return new TickerSummary(ticker.getSymbol(), ticker.getName(), ticker.getCurrency(), price, expectedCount, yieldResult, streakResult);
    }

    private DividendIncreaseStreakResult calculateStreak(Ticker ticker, int expectedCount, int currentYear) {
        List<DividendPayment> regularPayments =
                dividendPaymentRepository.findByTickerAndTypeOrderByExDividendDateAsc(ticker, DividendType.REGULAR);
        List<SplitEvent> allSplits = splitEventRepository.findByTickerOrderByExecutionDateAsc(ticker);
        return calculateStreak(regularPayments, allSplits, expectedCount, currentYear);
    }

    private DividendIncreaseStreakResult calculateStreak(
            List<DividendPayment> regularPayments, List<SplitEvent> allSplits, int expectedCount, int currentYear) {
        Map<Integer, BigDecimal> annualTotalsByYear = new HashMap<>();
        Map<Integer, Integer> paymentCountByYear = new HashMap<>();
        for (DividendPayment payment : regularPayments) {
            int year = payment.getExDividendDate().getYear();
            List<SplitEvent> laterSplits = allSplits.stream()
                    .filter(s -> s.getExecutionDate().isAfter(payment.getExDividendDate()))
                    .toList();
            BigDecimal adjusted = SplitAdjustmentCalculator.adjustedAmount(laterSplits, payment.getAmount());

            annualTotalsByYear.merge(year, adjusted, BigDecimal::add);
            paymentCountByYear.merge(year, 1, Integer::sum);
        }

        return DividendIncreaseStreakCalculator.evaluate(annualTotalsByYear, paymentCountByYear, expectedCount, currentYear);
    }
}
