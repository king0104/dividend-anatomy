package com.dividendanatomy.ingestion.twelvedata;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 이번 세션에서 실제로 받은 NVDA 응답(2024-06-10 10:1 분할 전후 7영업일)을
 * 그대로 테스트 리소스로 써서 역직렬화+매핑을 검증한다.
 */
class TwelveDataPriceMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void mapsRealCapturedNvdaResponseToPriceBars() throws Exception {
        TwelveDataTimeSeriesResponse response;
        try (InputStream in = getClass().getResourceAsStream("/twelvedata/nvda-time-series-2024-06.json")) {
            response = MAPPER.readValue(in, TwelveDataTimeSeriesResponse.class);
        }

        Ticker nvda = new Ticker("NVDA", "NVIDIA Corporation", "USD");
        List<PriceBar> bars = TwelveDataPriceMapper.toPriceBars(response, nvda);

        assertEquals(7, bars.size());
        // 알 수 없는 필드(open/high/low/volume)가 있어도 깨지지 않고 close만 매핑됐는지 확인
        PriceBar juneTenth = bars.stream()
                .filter(b -> b.getDate().equals(LocalDate.parse("2024-06-10")))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("121.79000").compareTo(juneTenth.getClose()));
        assertEquals(DataSource.TWELVE_DATA, juneTenth.getSource());

        // 분할 전후로 가격이 연속적이라는 사실 자체도 재확인(1200대로 튀지 않음)
        assertTrue(bars.stream().allMatch(b -> b.getClose().compareTo(new BigDecimal("200")) < 0));
    }
}
