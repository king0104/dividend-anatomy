package com.dividendanatomy.ingestion.massive;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Massive를 호출해서 배당 이력을 가져오고 DividendPaymentRepository에
 * 저장한다. 정기/특별배당 분류(dividend_type)와 연간 지급 횟수
 * (frequency)는 자체 추론하지 않고 Massive 응답을 그대로 신뢰한다
 * (docs/decisions/04-dividend-classification.md).
 */
@Service
public class MassiveDividendIngestionService {

    private final MassiveClient massiveClient;
    private final DividendPaymentRepository dividendPaymentRepository;
    private final TickerRepository tickerRepository;

    public MassiveDividendIngestionService(
            MassiveClient massiveClient,
            DividendPaymentRepository dividendPaymentRepository,
            TickerRepository tickerRepository) {
        this.massiveClient = massiveClient;
        this.dividendPaymentRepository = dividendPaymentRepository;
        this.tickerRepository = tickerRepository;
    }

    /** @return 새로 저장된 배당 건수 (이미 있던 것은 세지 않음) */
    @Transactional
    public int ingest(Ticker ticker) {
        List<MassiveDividend> fetched = massiveClient.fetchAllDividends(ticker.getSymbol());

        int savedCount = 0;
        LocalDate latestRegularExDate = null;
        Integer latestFrequency = null;

        for (MassiveDividend dividend : fetched) {
            LocalDate exDate = LocalDate.parse(dividend.exDividendDate());

            boolean alreadyExists = dividendPaymentRepository
                    .findByTickerAndExDividendDate(ticker, exDate)
                    .isPresent();
            if (!alreadyExists) {
                DividendPayment payment = MassiveDividendMapper.toDividendPayment(dividend, ticker);
                dividendPaymentRepository.save(payment);
                savedCount++;
            }

            if ("CD".equals(dividend.dividendType()) && dividend.frequency() > 0
                    && (latestRegularExDate == null || exDate.isAfter(latestRegularExDate))) {
                latestRegularExDate = exDate;
                latestFrequency = dividend.frequency();
            }
        }

        if (latestFrequency != null) {
            ticker.setRegularPaymentsPerYear(latestFrequency);
            tickerRepository.save(ticker);
        }

        return savedCount;
    }
}
