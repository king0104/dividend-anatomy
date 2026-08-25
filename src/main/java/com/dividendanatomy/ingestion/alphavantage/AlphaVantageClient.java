package com.dividendanatomy.ingestion.alphavantage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Alpha Vantage 재무제표 엔드포인트 4개만 담당하는 얇은 클라이언트.
 * 응답을 그대로 반환하고 파싱/매핑/저장은 하지 않는다 (CLAUDE.md: 서비스
 * 계층은 DB만 읽는다 — 외부 API 호출은 이 계층에서만). 무료 플랜은 초당
 * 1콜·하루 25콜 제한이 있다(docs/decisions/12) — 호출 간격 조절은
 * 호출부(AlphaVantageFinancialsIngestionService)의 책임.
 */
@Component
public class AlphaVantageClient {

    private final RestClient restClient;
    private final String apiKey;

    public AlphaVantageClient(
            RestClient.Builder builder,
            @Value("${alphavantage.base-url}") String baseUrl,
            @Value("${alphavantage.api-key}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public AlphaVantageOverviewResponse fetchOverview(String symbol) {
        return get("OVERVIEW", symbol, AlphaVantageOverviewResponse.class);
    }

    public AlphaVantageBalanceSheetResponse fetchBalanceSheet(String symbol) {
        return get("BALANCE_SHEET", symbol, AlphaVantageBalanceSheetResponse.class);
    }

    public AlphaVantageCashFlowResponse fetchCashFlow(String symbol) {
        return get("CASH_FLOW", symbol, AlphaVantageCashFlowResponse.class);
    }

    public AlphaVantageIncomeStatementResponse fetchIncomeStatement(String symbol) {
        return get("INCOME_STATEMENT", symbol, AlphaVantageIncomeStatementResponse.class);
    }

    private <T> T get(String function, String symbol, Class<T> responseType) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", function)
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(responseType);
    }
}
