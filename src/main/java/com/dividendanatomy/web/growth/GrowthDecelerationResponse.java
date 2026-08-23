package com.dividendanatomy.web.growth;

import com.dividendanatomy.domain.growth.GrowthDecelerationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * cagrShortPercent/cagrLongPercent는 계산 불가면 null,
 * decelerationGapPercent는 status == DECELERATING일 때만 값이 있다.
 */
public record GrowthDecelerationResponse(
        String tickerSymbol,
        LocalDate asOf,
        GrowthDecelerationStatus status,
        BigDecimal cagrShortPercent,
        BigDecimal cagrLongPercent,
        BigDecimal decelerationGapPercent) {
}
