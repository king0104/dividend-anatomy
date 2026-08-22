package com.dividendanatomy.ingestion.twelvedata;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.PriceBarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Twelve Data를 호출해서 가격 시계열을 가져오고 PriceBarRepository에
 * 저장한다. 이 클래스가 외부 API를 호출하는 유일한 지점 — 서비스/도메인
 * 계층은 이 결과만 DB로 읽는다 (CLAUDE.md).
 */
@Service
public class TwelveDataPriceIngestionService {

    private final TwelveDataClient twelveDataClient;
    private final PriceBarRepository priceBarRepository;

    public TwelveDataPriceIngestionService(TwelveDataClient twelveDataClient, PriceBarRepository priceBarRepository) {
        this.twelveDataClient = twelveDataClient;
        this.priceBarRepository = priceBarRepository;
    }

    /** @return 새로 저장(insert)되거나 갱신(update)된 건수 */
    @Transactional
    public int ingest(Ticker ticker, LocalDate startDate, LocalDate endDate) {
        TwelveDataTimeSeriesResponse response =
                twelveDataClient.fetchDailyTimeSeries(ticker.getSymbol(), startDate, endDate);
        List<PriceBar> fetched = TwelveDataPriceMapper.toPriceBars(response, ticker);

        int count = 0;
        for (PriceBar bar : fetched) {
            priceBarRepository.findByTickerAndDate(ticker, bar.getDate())
                    .ifPresentOrElse(
                            existing -> {
                                existing.updateClose(bar.getClose(), DataSource.TWELVE_DATA);
                                priceBarRepository.save(existing);
                            },
                            () -> priceBarRepository.save(bar));
            count++;
        }
        return count;
    }
}
