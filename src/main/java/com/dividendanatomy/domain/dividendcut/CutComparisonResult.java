package com.dividendanatomy.domain.dividendcut;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * decreasePercent는 status == CUT일 때만 값이 있고, 그 외엔 null
 * (docs/specs/dividend-cut-detection.md 1.4절).
 */
public record CutComparisonResult(
        LocalDate detectedAt,
        CutComparisonStatus status,
        BigDecimal previousTtmAmount,
        BigDecimal currentTtmAmount,
        BigDecimal decreasePercent) {
}
