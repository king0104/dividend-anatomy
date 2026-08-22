package com.dividendanatomy.ingestion.massive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MassiveSplit(
        @JsonProperty("execution_date") String executionDate,
        @JsonProperty("split_from") int splitFrom,
        @JsonProperty("split_to") int splitTo,
        String ticker) {
}
