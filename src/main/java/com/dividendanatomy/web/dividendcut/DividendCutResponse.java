package com.dividendanatomy.web.dividendcut;

import com.dividendanatomy.domain.dividendcut.CutComparisonStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DividendCutResponse(String tickerSymbol, List<CutEventDto> comparisons) {

    /** decreasePercent는 status == CUT일 때만 값이 있고, 그 외엔 null. */
    public record CutEventDto(
            LocalDate detectedAt,
            CutComparisonStatus status,
            BigDecimal previousTtmAmount,
            BigDecimal currentTtmAmount,
            BigDecimal decreasePercent) {
    }
}
