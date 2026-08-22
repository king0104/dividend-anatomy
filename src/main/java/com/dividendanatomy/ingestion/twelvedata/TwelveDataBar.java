package com.dividendanatomy.ingestion.twelvedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TwelveDataBar(String datetime, BigDecimal close) {
}
