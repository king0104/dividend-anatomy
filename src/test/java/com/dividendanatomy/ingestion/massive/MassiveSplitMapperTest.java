package com.dividendanatomy.ingestion.massive;

import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 이번 세션에서 실제로 받은 NVDA 분할 이력(4건)을 그대로 테스트 리소스로
 * 써서 역직렬화+ratio 계산을 검증한다. 2007년 3:2 분할(ratio=1.5)이
 * 정수 아닌 비율도 맞게 계산되는지 보는 손계산 케이스.
 */
class MassiveSplitMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void mapsRealCapturedNvdaSplitsWithCorrectRatios() throws Exception {
        MassiveSplitsResponse response;
        try (InputStream in = getClass().getResourceAsStream("/massive/nvda-splits.json")) {
            response = MAPPER.readValue(in, MassiveSplitsResponse.class);
        }

        Ticker nvda = new Ticker("NVDA", "NVIDIA Corporation", "USD");
        List<SplitEvent> events = response.results().stream()
                .map(split -> MassiveSplitMapper.toSplitEvent(split, nvda))
                .toList();

        assertEquals(4, events.size());
        assertRatio(events, "2024-06-10", "10");
        assertRatio(events, "2021-07-20", "4");
        assertRatio(events, "2007-09-11", "1.5"); // 3:2 분할 — 정수 아닌 비율
        assertRatio(events, "2006-04-07", "2");
    }

    private void assertRatio(List<SplitEvent> events, String date, String expectedRatio) {
        SplitEvent event = events.stream()
                .filter(e -> e.getExecutionDate().equals(LocalDate.parse(date)))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal(expectedRatio).compareTo(event.getRatio()));
    }
}
