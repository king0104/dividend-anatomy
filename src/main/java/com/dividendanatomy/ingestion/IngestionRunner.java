package com.dividendanatomy.ingestion;

import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.ingestion.alphavantage.AlphaVantageFinancialsIngestionService;
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
 * 가격 수집 기간은 {@code --ingest.priceYears=N}으로 조절 가능하고
 * 지정하지 않으면 기존과 동일하게 3년이다. 타임머신 시뮬레이터
 * 브랜드 풀처럼 더 긴 과거 가격이 필요할 때 20 등으로 늘려서 쓴다 —
 * Twelve Data 무료 플랜은 최소 20년까지 실키로 확인됨(docs/decisions/13).
 * 화면/스케줄러가 아직 없는 지금 단계에서만 쓰는 임시 진입점.
 *
 * <p>4번째 단계(재무 지표 수집)는 Alpha Vantage 무료 플랜 하루 25콜 제한에
 * 걸린다(호출 4번/티커, docs/decisions/12) — 여러 티커를 한 번에 돌릴 땐
 * 하루 6종목(24콜)을 넘기지 않도록 나눠서 실행할 것. 코드로 자동 회피하지
 * 않는다(과설계).
 */
@Component
@ConditionalOnProperty("ingest.ticker")
public class IngestionRunner implements ApplicationRunner {

    private final TickerRepository tickerRepository;
    private final TwelveDataPriceIngestionService priceIngestionService;
    private final MassiveSplitIngestionService splitIngestionService;
    private final MassiveDividendIngestionService dividendIngestionService;
    private final AlphaVantageFinancialsIngestionService financialsIngestionService;

    @Value("${ingest.ticker}")
    private String ingestSpec;

    @Value("${ingest.priceYears:3}")
    private int priceYears;

    public IngestionRunner(
            TickerRepository tickerRepository,
            TwelveDataPriceIngestionService priceIngestionService,
            MassiveSplitIngestionService splitIngestionService,
            MassiveDividendIngestionService dividendIngestionService,
            AlphaVantageFinancialsIngestionService financialsIngestionService) {
        this.tickerRepository = tickerRepository;
        this.priceIngestionService = priceIngestionService;
        this.splitIngestionService = splitIngestionService;
        this.dividendIngestionService = dividendIngestionService;
        this.financialsIngestionService = financialsIngestionService;
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
        LocalDate startDate = endDate.minusYears(priceYears);

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

        System.out.println("[ingest] " + symbol + " 재무 지표(배당 안전도) 수집 시작");
        financialsIngestionService.ingest(ticker);
        System.out.println("[ingest] " + symbol + " 재무 지표 반영 완료");
    }
}
