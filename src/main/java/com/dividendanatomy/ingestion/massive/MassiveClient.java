package com.dividendanatomy.ingestion.massive;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Massive /v3/reference/splits 호출만 담당하는 얇은 클라이언트.
 * next_url 페이지네이션을 따라가서 전체 결과를 모아 반환한다 —
 * next_url은 Massive가 이미 apiKey 쿼리 파라미터까지 포함해서 내려준다
 * (check_massive_api.py에서 확인한 패턴).
 */
@Component
public class MassiveClient {

    private final RestClient restClient;
    private final String apiKey;

    public MassiveClient(
            RestClient.Builder builder,
            @Value("${massive.base-url}") String baseUrl,
            @Value("${massive.api-key}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public List<MassiveSplit> fetchAllSplits(String ticker) {
        List<MassiveSplit> all = new ArrayList<>();

        MassiveSplitsResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v3/reference/splits")
                        .queryParam("ticker", ticker)
                        .queryParam("apiKey", apiKey)
                        .build())
                .retrieve()
                .body(MassiveSplitsResponse.class);

        while (response != null) {
            if (response.results() != null) {
                all.addAll(response.results());
            }
            String nextUrl = response.nextUrl();
            if (nextUrl == null || nextUrl.isBlank()) {
                break;
            }
            response = restClient.get().uri(URI.create(nextUrl)).retrieve().body(MassiveSplitsResponse.class);
        }

        return all;
    }
}
