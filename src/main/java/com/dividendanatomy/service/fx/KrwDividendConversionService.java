package com.dividendanatomy.service.fx;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.fx.KrwConvertedEntry;
import com.dividendanatomy.domain.market.ExchangeRate;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.tax.NetDividendEntry;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.ExchangeRateRepository;
import com.dividendanatomy.repository.TickerRepository;
import com.dividendanatomy.service.tax.UsWithholdingTaxService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * UsWithholdingTaxService(세후 USD)와 ExchangeRateRepository(배당 지급일
 * 기준 환율)를 조합해서 원화 환산 실수령액을 계산한다. DB만 읽는다 —
 * 외부 API 호출 없음(CLAUDE.md "서비스 계층은 DB만 읽는다").
 */
@Service
public class KrwDividendConversionService {

    private static final String FROM_CURRENCY = "USD";
    private static final String TO_CURRENCY = "KRW";

    private final TickerRepository tickerRepository;
    private final DividendPaymentRepository dividendPaymentRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final UsWithholdingTaxService usWithholdingTaxService;

    public KrwDividendConversionService(
            TickerRepository tickerRepository,
            DividendPaymentRepository dividendPaymentRepository,
            ExchangeRateRepository exchangeRateRepository,
            UsWithholdingTaxService usWithholdingTaxService) {
        this.tickerRepository = tickerRepository;
        this.dividendPaymentRepository = dividendPaymentRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.usWithholdingTaxService = usWithholdingTaxService;
    }

    public List<KrwConvertedEntry> getKrwConvertedDividends(String symbol) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("알 수 없는 티커: " + symbol));

        List<NetDividendEntry> usdEntries = usWithholdingTaxService.getNetDividends(symbol).entries();
        Map<LocalDate, LocalDate> payDateByExDividendDate = new HashMap<>();
        for (DividendPayment payment : dividendPaymentRepository.findByTickerOrderByExDividendDateAsc(ticker)) {
            payDateByExDividendDate.put(payment.getExDividendDate(), payment.getPayDate());
        }

        return usdEntries.stream()
                .map(usdEntry -> toKrwEntry(usdEntry, payDateByExDividendDate))
                .toList();
    }

    private KrwConvertedEntry toKrwEntry(NetDividendEntry usdEntry, Map<LocalDate, LocalDate> payDateByExDividendDate) {
        LocalDate payDate = payDateByExDividendDate.get(usdEntry.exDividendDate());
        Optional<ExchangeRate> rate = payDate == null
                ? Optional.empty()
                : exchangeRateRepository.findTopByFromCurrencyAndToCurrencyAndDateLessThanEqualOrderByDateDesc(
                        FROM_CURRENCY, TO_CURRENCY, payDate);
        return KrwConvertedEntry.convert(usdEntry, payDate, rate);
    }
}
