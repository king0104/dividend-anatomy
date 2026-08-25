package com.dividendanatomy.ingestion.alphavantage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Alpha Vantage CASH_FLOW 응답. 최신 값은 annualReports.get(0). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlphaVantageCashFlowResponse(List<Report> annualReports) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Report(
            @JsonProperty("fiscalDateEnding") String fiscalDateEnding,
            @JsonProperty("operatingCashflow") String operatingCashflow,
            @JsonProperty("capitalExpenditures") String capitalExpenditures,
            @JsonProperty("dividendPayout") String dividendPayout) {
    }
}
