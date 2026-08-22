package com.dividendanatomy.ingestion;

import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.ingestion.massive.MassiveDividendIngestionService;
import com.dividendanatomy.ingestion.massive.MassiveSplitIngestionService;
import com.dividendanatomy.ingestion.twelvedata.TwelveDataPriceIngestionService;
import com.dividendanatomy.repository.TickerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 실제 티커 데이터를 한 번 수집해 넣기 위한 수동 트리거.
 * {@code --ingest.ticker=SYMBOL:종목명:통화} 인자가 있을 때만 동작한다
 * (예: {@code --ingest.ticker=KO:The Coca-Cola Company:USD}).
 * 화면/스케줄러가 아직 없는 지금 단계에서만 쓰는 임시 진입점.
 */
@Component
@ConditionalOnProperty("ingest.ticker")
public class IngestionRunner implements ApplicationRunner {

    private final TickerRepository tickerRepository;
    private final TwelveDataPriceIngestionService priceIngestionService;
    private final MassiveSplitIngestionService splitIngestionService;
    private final MassiveDividendIngestionService dividendIngestionService;

    @Value("${ingest.ticker}")
    private String ingestSpec;

    public IngestionRunner(
            TickerRepository tickerRepository,
            TwelveDataPriceIngestionService priceIngestionService,
            MassiveSplitIngestionService splitIngestionService,
            MassiveDividendIngestionService dividendIngestionService) {
        this.tickerRepository = tickerRepository;
        this.priceIngestionService = priceIngestionService;
        this.splitIngestionService = splitIngestionService;
        this.dividendIngestionService = dividendIngestionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] parts = ingestSpec.split(":", 3);
        String symbol = parts[0];
        String name = parts.length > 1 ? parts[1] : symbol;
        String currency = parts.length > 2 ? parts[2] : "USD";

        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseGet(() -> tickerRepository.save(new Ticker(symbol, name, currency)));

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(3);

        System.out.println("[ingest] " + symbol + " 가격 수집 시작 (" + startDate + " ~ " + endDate + ")");
        int priceCount = priceIngestionService.ingest(ticker, startDate, endDate);
        System.out.println("[ingest] " + symbol + " 가격 " + priceCount + "건 반영");

        System.out.println("[ingest] " + symbol + " 분할 이력 수집 시작");
        int splitCount = splitIngestionService.ingest(ticker);
        System.out.println("[ingest] " + symbol + " 분할 " + splitCount + "건 신규 저장");

        System.out.println("[ingest] " + symbol + " 배당 이력 수집 시작");
        int dividendCount = dividendIngestionService.ingest(ticker);
        System.out.println("[ingest] " + symbol + " 배당 " + dividendCount + "건 신규 저장, regularPaymentsPerYear="
                + tickerRepository.findBySymbol(symbol).map(Ticker::getRegularPaymentsPerYear).orElse(null));
    }
}
