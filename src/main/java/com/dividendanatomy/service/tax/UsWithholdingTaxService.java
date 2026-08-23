package com.dividendanatomy.service.tax;

import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.tax.NetDividendSummary;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * 티커 심볼을 받아 미국 원천징수 15% 적용 후 실수령액 목록을 계산한다.
 * DB만 읽는다 — 외부 API 호출 없음(CLAUDE.md "서비스 계층은 DB만 읽는다").
 */
@Service
public class UsWithholdingTaxService {

    private final TickerRepository tickerRepository;
    private final DividendPaymentRepository dividendPaymentRepository;

    public UsWithholdingTaxService(TickerRepository tickerRepository, DividendPaymentRepository dividendPaymentRepository) {
        this.tickerRepository = tickerRepository;
        this.dividendPaymentRepository = dividendPaymentRepository;
    }

    public NetDividendSummary getNetDividends(String symbol) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("알 수 없는 티커: " + symbol));
        if (!"USD".equals(ticker.getCurrency())) {
            throw new IllegalStateException(
                    "%s: 이 지표는 미국 원천징수(USD)만 계산하며 통화가 USD가 아닌 종목은 지원하지 않는다".formatted(symbol));
        }
        return NetDividendSummary.from(dividendPaymentRepository.findByTickerOrderByExDividendDateAsc(ticker));
    }
}
