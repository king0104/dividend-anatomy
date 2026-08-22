package com.dividendanatomy.ingestion.twelvedata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

/**
 * Twelve Data /time_series 호출만 담당하는 얇은 클라이언트. 응답을 그대로
 * 반환하고 파싱/매핑/저장은 하지 않는다 (CLAUDE.md: 서비스 계층은
 * DB만 읽는다 — 외부 API 호출은 이 계층에서만).
 */
@Component
public class TwelveDataClient {

    private final RestClient restClient;
    private final String apiKey;

    public TwelveDataClient(
            RestClient.Builder builder,
            @Value("${twelvedata.base-url}") String baseUrl,
            @Value("${twelvedata.api-key}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public TwelveDataTimeSeriesResponse fetchDailyTimeSeries(String symbol, LocalDate startDate, LocalDate endDate) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/time_series")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", "1day")
                        .queryParam("start_date", startDate)
                        .queryParam("end_date", endDate)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(TwelveDataTimeSeriesResponse.class);
    }
}
