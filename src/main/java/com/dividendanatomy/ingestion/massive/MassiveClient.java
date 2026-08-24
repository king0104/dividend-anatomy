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
 * next_url 페이지네이션을 따라가서 전체 결과를 모아 반환한다 — next_url에는
 * apiKey가 포함돼 있지 않으므로(예: KO는 결과가 10건 미만이라 페이지네이션
 * 자체가 발생하지 않아 초기 검증에서 못 잡음. CBSH/TR처럼 분할 이력이 10건을
 * 넘는 종목에서 401 "API Key was not provided"로 실제로 드러남) 매번 직접
 * 붙여준다.
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
            String separator = nextUrl.contains("?") ? "&" : "?";
            URI nextUri = URI.create(nextUrl + separator + "apiKey=" + apiKey);
            response = restClient.get().uri(nextUri).retrieve().body(responseType);
        }

        return all;
    }
}
