package com.dividendanatomy.web.yield;

import com.dividendanatomy.domain.yield.YieldContribution;
import com.dividendanatomy.service.yield.YieldDecompositionResult;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * YieldDecompositionResult(원시 비율, 무반올림) → YieldDecompositionResponse
 * (%p, 소수 2자리 HALF_UP) 순수 변환. 반올림은 이 지점에서 딱 한 번만
 * 일어난다 (docs/specs/yield-change-decomposition.md 3절).
 */
public final class YieldDecompositionResponseMapper {

    private static final int PERCENT_SCALE = 2;

    private YieldDecompositionResponseMapper() {
    }

    public static YieldDecompositionResponse toResponse(YieldDecompositionResult result) {
        YieldDecompositionResponse.ContributionDto actual = toContributionDto(result.actual());
        YieldDecompositionResponse.ContributionDto annualized = result.annualized()
                .map(YieldDecompositionResponseMapper::toContributionDto)
                .orElse(null);

        YieldDecompositionResponse.DataQuality dataQuality = new YieldDecompositionResponse.DataQuality(
                result.usedFallbackPriceAtT0(),
                result.usedFallbackPriceAtT1(),
                result.ttmAtT0().isComplete(),
                result.ttmAtT1().isComplete());

        return new YieldDecompositionResponse(
                result.tickerSymbol(), result.t0(), result.t1(), actual, annualized, dataQuality);
    }

    private static YieldDecompositionResponse.ContributionDto toContributionDto(YieldContribution contribution) {
        return new YieldDecompositionResponse.ContributionDto(
                toPercent(contribution.priceContribution()),
                toPercent(contribution.dividendContribution()));
    }

    private static BigDecimal toPercent(BigDecimal rawRatio) {
        return rawRatio.multiply(BigDecimal.valueOf(100)).setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
