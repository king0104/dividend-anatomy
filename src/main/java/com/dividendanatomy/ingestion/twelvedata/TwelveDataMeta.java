package com.dividendanatomy.ingestion.twelvedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TwelveDataMeta(String symbol, String interval, String currency) {
}
