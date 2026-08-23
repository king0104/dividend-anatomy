package com.dividendanatomy.domain.growth;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * decelerationGap은 status == DECELERATING일 때만 값이 있고, 그 외엔 empty.
 */
public record GrowthDecelerationResult(
        Optional<BigDecimal> cagrShort,
        Optional<BigDecimal> cagrLong,
        GrowthDecelerationStatus status,
        Optional<BigDecimal> decelerationGap) {
}
