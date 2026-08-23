package com.dividendanatomy.web.growth;

import com.dividendanatomy.domain.growth.GrowthDecelerationResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * %p 반올림(scale=2, HALF_UP)을 응답 매핑 시점에만 적용한다
 * (docs/specs/dividend-growth-deceleration.md 3절).
 */
public final class GrowthDecelerationResponseMapper {

    private GrowthDecelerationResponseMapper() {
    }

    public static GrowthDecelerationResponse toResponse(String symbol, LocalDate asOf, GrowthDecelerationResult result) {
        return new GrowthDecelerationResponse(
                symbol,
                asOf,
                result.status(),
                toPercent(result.cagrShort()),
                toPercent(result.cagrLong()),
                toPercent(result.decelerationGap()));
    }

    private static BigDecimal toPercent(Optional<BigDecimal> raw) {
        return raw.map(value -> value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
    }
}
