package com.dividendanatomy.repository;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 실제 Massive API로 확인한 KO 분할 이력을 그대로 시드 데이터로 쓴다
 * (docs/decisions/03-split-adjustment.md — 2012-08-13, split_from=1,
 * split_to=2 → ratio=2).
 */
@DataJpaTest
class SplitEventRepositoryTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private SplitEventRepository splitEventRepository;

    @Test
    void returnsOnlySplitsAfterGivenDateInOrder() {
        Ticker ko = tickerRepository.save(new Ticker("KO", "The Coca-Cola Company", "USD"));

        splitEventRepository.save(new SplitEvent(ko, LocalDate.parse("2012-08-13"),
                new BigDecimal("2"), DataSource.MASSIVE));

        // 2012-08-13보다 이전에 지급된 배당(예: 2012-06-13) 기준으로 조회하면
        // 이 분할이 나와야 한다 — TTM 집계에서 "이 배당 이후 발생한 분할"을 찾는 용도.
        List<SplitEvent> afterJune = splitEventRepository
                .findByTickerAndExecutionDateAfterOrderByExecutionDateAsc(ko, LocalDate.parse("2012-06-13"));
        assertEquals(1, afterJune.size());
        assertEquals(0, new BigDecimal("2").compareTo(afterJune.get(0).getRatio()));

        // 분할 이후 지급된 배당(예: 2012-09-12) 기준으로 조회하면 이 분할은 안 나와야 한다.
        List<SplitEvent> afterSeptember = splitEventRepository
                .findByTickerAndExecutionDateAfterOrderByExecutionDateAsc(ko, LocalDate.parse("2012-09-12"));
        assertEquals(0, afterSeptember.size());
    }
}
