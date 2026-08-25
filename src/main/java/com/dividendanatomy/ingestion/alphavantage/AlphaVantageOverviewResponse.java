package com.dividendanatomy.ingestion.alphavantage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Alpha Vantage OVERVIEW 응답 중 배당 안전도 스코어에 필요한 필드만.
 * 숫자류는 전부 JSON 문자열로 오고, 결측은 문자열 "None"으로 온다
 * (docs/decisions/14) — String으로 받아 매퍼에서 명시적으로 파싱한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlphaVantageOverviewResponse(
        @JsonProperty("ReturnOnEquityTTM") String returnOnEquityTtm,
        @JsonProperty("DividendPerShare") String dividendPerShare,
        @JsonProperty("EPS") String eps) {
}
