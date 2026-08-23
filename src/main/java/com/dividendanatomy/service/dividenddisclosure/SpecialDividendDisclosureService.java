package com.dividendanatomy.service.dividenddisclosure;

import com.dividendanatomy.domain.dividenddisclosure.SpecialDividendDisclosure;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * 티커 심볼을 받아 전체 배당 지급 이력 중 특별배당으로 분류돼 제외된
 * 건과 그 근거를 보여준다. DB만 읽는다 — 외부 API 호출 없음
 * (CLAUDE.md "서비스 계층은 DB만 읽는다").
 */
@Service
public class SpecialDividendDisclosureService {

    private final TickerRepository tickerRepository;
    private final DividendPaymentRepository dividendPaymentRepository;

    public SpecialDividendDisclosureService(
            TickerRepository tickerRepository, DividendPaymentRepository dividendPaymentRepository) {
        this.tickerRepository = tickerRepository;
        this.dividendPaymentRepository = dividendPaymentRepository;
    }

    public SpecialDividendDisclosure getDisclosure(String symbol) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("알 수 없는 티커: " + symbol));
        return SpecialDividendDisclosure.from(
                dividendPaymentRepository.findByTickerOrderByExDividendDateAsc(ticker));
    }
}
