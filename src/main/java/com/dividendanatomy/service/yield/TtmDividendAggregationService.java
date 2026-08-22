package com.dividendanatomy.service.yield;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.yield.TtmDividendSummary;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.SplitEventRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;

/**
 * raw 배당(DividendPayment.amount)을 분할 조정해서 TTM 합계를 만든다.
 * 조정 방향: 배당 이후 발생한 분할 비율의 누적곱으로 나눠서 "현재 주식 수"
 * 기준으로 환산한다 (docs/decisions/03-split-adjustment.md).
 */
@Service
public class TtmDividendAggregationService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final DividendPaymentRepository dividendPaymentRepository;
    private final SplitEventRepository splitEventRepository;

    public TtmDividendAggregationService(
            DividendPaymentRepository dividendPaymentRepository, SplitEventRepository splitEventRepository) {
        this.dividendPaymentRepository = dividendPaymentRepository;
        this.splitEventRepository = splitEventRepository;
    }

    public TtmDividendSummary summarize(Ticker ticker, LocalDate windowEnd, int expectedCount) {
        LocalDate windowStart = windowEnd.minusMonths(12);
        List<DividendPayment> payments = dividendPaymentRepository
                .findByTickerAndTypeAndExDividendDateBetweenOrderByExDividendDateAsc(
                        ticker, DividendType.REGULAR, windowStart, windowEnd);

        BigDecimal actualSum = BigDecimal.ZERO;
        for (DividendPayment payment : payments) {
            actualSum = actualSum.add(splitAdjustedAmount(ticker, payment), MC);
        }

        int foundCount = payments.size();
        BigDecimal annualizedSum = foundCount == 0
                ? null
                : actualSum.multiply(BigDecimal.valueOf(expectedCount), MC)
                        .divide(BigDecimal.valueOf(foundCount), MC);

        return new TtmDividendSummary(actualSum, annualizedSum, foundCount, expectedCount);
    }

    private BigDecimal splitAdjustedAmount(Ticker ticker, DividendPayment payment) {
        List<SplitEvent> laterSplits = splitEventRepository
                .findByTickerAndExecutionDateAfterOrderByExecutionDateAsc(ticker, payment.getExDividendDate());

        BigDecimal cumulativeRatio = BigDecimal.ONE;
        for (SplitEvent split : laterSplits) {
            cumulativeRatio = cumulativeRatio.multiply(split.getRatio(), MC);
        }

        return payment.getAmount().divide(cumulativeRatio, MC);
    }
}
