package com.dividendanatomy.service.dividendcut;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.dividendcut.CutComparisonResult;
import com.dividendanatomy.domain.dividendcut.DividendCutDetector;
import com.dividendanatomy.domain.dividendcut.TtmSnapshot;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.TickerRepository;
import com.dividendanatomy.service.yield.TtmDividendAggregationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 티커 심볼을 받아 정기 배당 지급 이력 전체에서 삭감 구간을 찾는다.
 * DB만 읽는다 — 외부 API 호출 없음 (CLAUDE.md "서비스 계층은 DB만 읽는다").
 */
@Service
public class DividendCutDetectionService {

    private final TickerRepository tickerRepository;
    private final DividendPaymentRepository dividendPaymentRepository;
    private final TtmDividendAggregationService ttmDividendAggregationService;

    public DividendCutDetectionService(
            TickerRepository tickerRepository,
            DividendPaymentRepository dividendPaymentRepository,
            TtmDividendAggregationService ttmDividendAggregationService) {
        this.tickerRepository = tickerRepository;
        this.dividendPaymentRepository = dividendPaymentRepository;
        this.ttmDividendAggregationService = ttmDividendAggregationService;
    }

    public List<CutComparisonResult> detectCuts(String symbol) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("알 수 없는 티커: " + symbol));

        Integer expectedCount = ticker.getRegularPaymentsPerYear();
        if (expectedCount == null) {
            throw new IllegalStateException(
                    "%s: 연간 정기 배당 지급 횟수가 설정되지 않음 (regularPaymentsPerYear)".formatted(symbol));
        }

        List<DividendPayment> payments = dividendPaymentRepository
                .findByTickerAndTypeOrderByExDividendDateAsc(ticker, DividendType.REGULAR);

        List<TtmSnapshot> snapshots = payments.stream()
                .map(payment -> new TtmSnapshot(
                        payment.getExDividendDate(),
                        ttmDividendAggregationService.summarize(ticker, payment.getExDividendDate(), expectedCount)))
                .toList();

        return new DividendCutDetector().detect(snapshots);
    }
}
