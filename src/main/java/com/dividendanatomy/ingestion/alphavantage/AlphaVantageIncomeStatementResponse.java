package com.dividendanatomy.ingestion.alphavantage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Alpha Vantage INCOME_STATEMENT 응답. 최신 값은 annualReports.get(0). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlphaVantageIncomeStatementResponse(List<Report> annualReports) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Report(
            @JsonProperty("fiscalDateEnding") String fiscalDateEnding,
            @JsonProperty("ebit") String ebit,
            @JsonProperty("interestExpense") String interestExpense) {
    }
}
