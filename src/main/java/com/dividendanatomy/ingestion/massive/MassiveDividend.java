package com.dividendanatomy.ingestion.massive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MassiveDividend(
        @JsonProperty("cash_amount") BigDecimal cashAmount,
        @JsonProperty("dividend_type") String dividendType,
        @JsonProperty("ex_dividend_date") String exDividendDate,
        @JsonProperty("record_date") String recordDate,
        @JsonProperty("pay_date") String payDate,
        int frequency,
        String ticker) {
}
