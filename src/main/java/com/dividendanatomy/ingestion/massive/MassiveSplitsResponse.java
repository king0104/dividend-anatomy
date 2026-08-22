package com.dividendanatomy.ingestion.massive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MassiveSplitsResponse(
        List<MassiveSplit> results,
        String status,
        @JsonProperty("next_url") String nextUrl) {
}
