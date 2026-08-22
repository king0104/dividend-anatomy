package com.dividendanatomy.ingestion.massive;

import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.SplitEventRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class MassiveSplitIngestionServiceTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private SplitEventRepository splitEventRepository;

    private static MassiveClient stubClient(List<MassiveSplit> fixedSplits) {
        return new MassiveClient(RestClient.builder(), "http://unused", "unused") {
            @Override
            public List<MassiveSplit> fetchAllSplits(String ticker) {
                return fixedSplits;
            }
        };
    }

    @Test
    void savesNewSplitsOnly() {
        Ticker nvda = tickerRepository.save(new Ticker("NVDA", "NVIDIA Corporation", "USD"));
        // 이미 저장된 분할 하나 (중복 방지 확인용)
        splitEventRepository.save(new SplitEvent(nvda, LocalDate.parse("2021-07-20"),
                new BigDecimal("4"), com.dividendanatomy.domain.market.DataSource.MASSIVE));

        MassiveSplitIngestionService service = new MassiveSplitIngestionService(
                stubClient(List.of(
                        new MassiveSplit("2024-06-10", 1, 10, "NVDA"),
                        new MassiveSplit("2021-07-20", 1, 4, "NVDA") // 이미 존재 — 건너뛰어야 함
                )),
                splitEventRepository);

        int savedCount = service.ingest(nvda);

        assertEquals(1, savedCount); // 새로 저장된 건 1건뿐
        List<SplitEvent> all = splitEventRepository.findAll();
        assertEquals(2, all.size()); // 전체는 기존 1 + 신규 1 = 2, 중복 없음
    }
}
