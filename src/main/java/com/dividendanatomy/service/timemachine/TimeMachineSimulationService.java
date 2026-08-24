package com.dividendanatomy.service.timemachine;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.ExchangeRate;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.split.SplitAdjustmentCalculator;
import com.dividendanatomy.domain.timemachine.InvestMode;
import com.dividendanatomy.domain.timemachine.TimeMachineResult;
import com.dividendanatomy.domain.timemachine.TimeMachineSimulator;
import com.dividendanatomy.domain.timemachine.YearlyCheckpoint;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.ExchangeRateRepository;
import com.dividendanatomy.repository.PriceBarRepository;
import com.dividendanatomy.repository.SplitEventRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 티커 심볼 + 투자 방식/금액(원화)/기간/기준일(asOf)을 받아 "재투자했다면
 * vs 안 했다면"을 계산한다. DB만 읽는다 — 외부 API 호출 없음(CLAUDE.md
 * "서비스 계층은 DB만 읽는다"). LocalDate.now()를 직접 쓰지 않고 asOf를
 * 호출부(컨트롤러)에서 받는다 — 다른 서비스(DividendGrowthDecelerationService
 * 등)와 동일한 관례, 테스트에서 날짜를 고정하기 위함이다.
 *
 * 원화 환산은 "asOf(사실상 오늘) 환율" 하나만 쓴다 —
 * KrwDividendConversionService의 "배당 지급일 기준" 원칙과 다르게,
 * 이 기능은 과거 환율을 재현하는 게 아니라 "지금 투자했다면"이라는
 * 가정을 전제로 하기 때문이다.
 */
@Service
public class TimeMachineSimulationService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final String USD = "USD";
    private static final String KRW = "KRW";

    private final TickerRepository tickerRepository;
    private final DividendPaymentRepository dividendPaymentRepository;
    private final PriceBarRepository priceBarRepository;
    private final SplitEventRepository splitEventRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public TimeMachineSimulationService(
            TickerRepository tickerRepository,
            DividendPaymentRepository dividendPaymentRepository,
            PriceBarRepository priceBarRepository,
            SplitEventRepository splitEventRepository,
            ExchangeRateRepository exchangeRateRepository) {
        this.tickerRepository = tickerRepository;
        this.dividendPaymentRepository = dividendPaymentRepository;
        this.priceBarRepository = priceBarRepository;
        this.splitEventRepository = splitEventRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    public TimeMachineSimulationResult simulate(
            String symbol, InvestMode mode, BigDecimal amountKrw, int requestedPeriodYears, LocalDate asOf) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("알 수 없는 티커: " + symbol));

        LocalDate endDate = asOf;
        LocalDate requestedStartDate = endDate.minusYears(requestedPeriodYears);

        PriceBar earliestAvailable = priceBarRepository.findTopByTickerOrderByDateAsc(ticker)
                .orElseThrow(() -> new NoSuchElementException("가격 데이터 없음: " + symbol));
        LocalDate actualStartDate = earliestAvailable.getDate().isAfter(requestedStartDate)
                ? earliestAvailable.getDate()
                : requestedStartDate;
        int actualPeriodYears = Period.between(actualStartDate, endDate).getYears();

        ExchangeRate rate = exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyAndDateLessThanEqualOrderByDateDesc(USD, KRW, endDate)
                .orElseThrow(() -> new IllegalStateException("USD/KRW 환율 데이터 없음"));

        BigDecimal startPrice = priceAt(ticker, actualStartDate);
        List<YearlyCheckpoint> checkpoints = buildYearlyCheckpoints(ticker, actualStartDate, endDate);

        BigDecimal principalUsd = mode == InvestMode.LUMP_SUM
                ? amountKrw.divide(rate.getRate(), MC)
                : BigDecimal.ZERO;
        BigDecimal monthlyContributionUsd = mode == InvestMode.MONTHLY
                ? amountKrw.divide(rate.getRate(), MC)
                : BigDecimal.ZERO;

        TimeMachineResult usdResult = TimeMachineSimulator.simulate(
                startPrice, checkpoints, principalUsd, monthlyContributionUsd, mode);

        return new TimeMachineSimulationResult(
                symbol,
                requestedPeriodYears,
                actualPeriodYears,
                actualPeriodYears >= requestedPeriodYears,
                usdResult,
                rate.getRate(),
                usdResult.finalValueReinvestUsd().multiply(rate.getRate(), MC),
                usdResult.finalValueNoReinvestUsd().multiply(rate.getRate(), MC),
                usdResult.differenceUsd().multiply(rate.getRate(), MC));
    }

    private List<YearlyCheckpoint> buildYearlyCheckpoints(Ticker ticker, LocalDate startDate, LocalDate endDate) {
        List<SplitEvent> allSplits = splitEventRepository.findByTickerOrderByExecutionDateAsc(ticker);

        List<YearlyCheckpoint> checkpoints = new ArrayList<>();
        LocalDate windowStart = startDate;
        while (windowStart.isBefore(endDate)) {
            LocalDate windowEnd = windowStart.plusYears(1);
            if (windowEnd.isAfter(endDate)) {
                windowEnd = endDate;
            }

            BigDecimal dividendPerShare = sumSplitAdjustedDividends(ticker, allSplits, windowStart, windowEnd);
            BigDecimal price = priceAt(ticker, windowEnd);
            checkpoints.add(new YearlyCheckpoint(windowEnd, dividendPerShare, price));

            windowStart = windowEnd;
        }
        return checkpoints;
    }

    private BigDecimal sumSplitAdjustedDividends(
            Ticker ticker, List<SplitEvent> allSplits, LocalDate startExclusive, LocalDate endInclusive) {
        List<DividendPayment> payments = dividendPaymentRepository
                .findByTickerAndTypeAndExDividendDateAfterAndExDividendDateLessThanEqualOrderByExDividendDateAsc(
                        ticker, DividendType.REGULAR, startExclusive, endInclusive);

        BigDecimal sum = BigDecimal.ZERO;
        for (DividendPayment payment : payments) {
            List<SplitEvent> laterSplits = allSplits.stream()
                    .filter(split -> split.getExecutionDate().isAfter(payment.getExDividendDate()))
                    .toList();
            sum = sum.add(SplitAdjustmentCalculator.adjustedAmount(laterSplits, payment.getAmount()), MC);
        }
        return sum;
    }

    private BigDecimal priceAt(Ticker ticker, LocalDate date) {
        return priceBarRepository.findTopByTickerAndDateLessThanEqualOrderByDateDesc(ticker, date)
                .map(PriceBar::getClose)
                .orElseThrow(() -> new NoSuchElementException(
                        "%s: %s 이전 가격 데이터 없음".formatted(ticker.getSymbol(), date)));
    }
}
