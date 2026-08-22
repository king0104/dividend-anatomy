package com.dividendanatomy.ingestion.massive;

import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.SplitEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Massive를 호출해서 분할 이력을 가져오고 SplitEventRepository에 저장한다.
 * 분할 이력은 사실상 불변이므로, 이미 저장된 (ticker, execution_date)는
 * 건너뛴다 (DividendPayment처럼 재조정될 일이 없음 — PriceBar와 다름).
 */
@Service
public class MassiveSplitIngestionService {

    private final MassiveClient massiveClient;
    private final SplitEventRepository splitEventRepository;

    public MassiveSplitIngestionService(MassiveClient massiveClient, SplitEventRepository splitEventRepository) {
        this.massiveClient = massiveClient;
        this.splitEventRepository = splitEventRepository;
    }

    /** @return 새로 저장된 건수 (이미 있던 것은 세지 않음) */
    @Transactional
    public int ingest(Ticker ticker) {
        List<MassiveSplit> fetched = massiveClient.fetchAllSplits(ticker.getSymbol());

        int savedCount = 0;
        for (MassiveSplit split : fetched) {
            LocalDate executionDate = LocalDate.parse(split.executionDate());
            boolean alreadyExists = splitEventRepository
                    .findByTickerAndExecutionDate(ticker, executionDate)
                    .isPresent();
            if (!alreadyExists) {
                splitEventRepository.save(MassiveSplitMapper.toSplitEvent(split, ticker));
                savedCount++;
            }
        }
        return savedCount;
    }
}
