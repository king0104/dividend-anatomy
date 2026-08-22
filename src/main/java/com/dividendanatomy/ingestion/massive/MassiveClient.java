package com.dividendanatomy.ingestion.massive;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Massive /v3/reference/(splits|dividends) 호출만 담당하는 얇은 클라이언트.
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
        return fetchAllPaginated(
                MassiveSplitsResponse.class,
                uriBuilder -> uriBuilder.path("/v3/reference/splits")
                        .queryParam("ticker", ticker)
                        .queryParam("apiKey", apiKey)
                        .build());
    }

    public List<MassiveDividend> fetchAllDividends(String ticker) {
        return fetchAllPaginated(
                MassiveDividendsResponse.class,
                uriBuilder -> uriBuilder.path("/v3/reference/dividends")
                        .queryParam("ticker", ticker)
                        .queryParam("limit", 1000)
                        .queryParam("apiKey", apiKey)
                        .build());
    }

    private <T, R extends MassivePaginatedResponse<T>> List<T> fetchAllPaginated(
            Class<R> responseType, Function<org.springframework.web.util.UriBuilder, URI> firstUri) {
        List<T> all = new ArrayList<>();

        R response = restClient.get().uri(firstUri).retrieve().body(responseType);

        while (response != null) {
            if (response.results() != null) {
                all.addAll(response.results());
            }
            String nextUrl = response.nextUrl();
            if (nextUrl == null || nextUrl.isBlank()) {
                break;
            }
            response = restClient.get().uri(URI.create(nextUrl)).retrieve().body(responseType);
        }

        return all;
    }
}
