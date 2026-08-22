package com.dividendanatomy.ingestion.twelvedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TwelveDataTimeSeriesResponse(TwelveDataMeta meta, List<TwelveDataBar> values, String status) {
}
