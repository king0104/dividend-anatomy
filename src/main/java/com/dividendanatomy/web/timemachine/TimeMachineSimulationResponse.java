package com.dividendanatomy.web.timemachine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TimeMachineSimulationResponse(
        String tickerSymbol,
        int requestedPeriodYears,
        int actualPeriodYears,
        boolean dataComplete,
        BigDecimal finalValueReinvestKrw,
        BigDecimal finalValueNoReinvestKrw,
        BigDecimal differenceKrw,
        BigDecimal totalReturnPercent,
        List<YearlySnapshotDto> yearlySeries) {

    public record YearlySnapshotDto(LocalDate checkpointDate, BigDecimal reinvestValueKrw, BigDecimal noReinvestValueKrw) {
    }
}
