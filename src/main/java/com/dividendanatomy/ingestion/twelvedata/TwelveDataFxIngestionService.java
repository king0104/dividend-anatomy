package com.dividendanatomy.ingestion.twelvedata;

import com.dividendanatomy.domain.market.ExchangeRate;
import com.dividendanatomy.repository.ExchangeRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Twelve Data forex 페어를 호출해서 일별 환율을 가져오고
 * ExchangeRateRepository에 저장한다 (docs/decisions/07-fx-data-source.md
 * — 기존 가격 조회와 응답 스키마가 동일해 TwelveDataClient를 그대로
 * 재사용). 과거 환율은 재조정되지 않으므로 이미 있으면 건너뛴다
 * (Massive 배당 수집과 동일한 멱등성 패턴, PriceBar의 "항상 갱신"과는 다름).
 */
@Service
public class TwelveDataFxIngestionService {

    private final TwelveDataClient twelveDataClient;
    private final ExchangeRateRepository exchangeRateRepository;

    public TwelveDataFxIngestionService(TwelveDataClient twelveDataClient, ExchangeRateRepository exchangeRateRepository) {
        this.twelveDataClient = twelveDataClient;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /** @return 새로 저장된 건수 (이미 있던 것은 세지 않음) */
    @Transactional
    public int ingest(String fromCurrency, String toCurrency, LocalDate startDate, LocalDate endDate) {
        TwelveDataTimeSeriesResponse response =
                twelveDataClient.fetchDailyTimeSeries(fromCurrency + "/" + toCurrency, startDate, endDate);
        List<ExchangeRate> fetched = TwelveDataFxMapper.toExchangeRates(response, fromCurrency, toCurrency);

        int savedCount = 0;
        for (ExchangeRate rate : fetched) {
            boolean alreadyExists = exchangeRateRepository
                    .findByFromCurrencyAndToCurrencyAndDate(fromCurrency, toCurrency, rate.getDate())
                    .isPresent();
            if (!alreadyExists) {
                exchangeRateRepository.save(rate);
                savedCount++;
            }
        }
        return savedCount;
    }
}
