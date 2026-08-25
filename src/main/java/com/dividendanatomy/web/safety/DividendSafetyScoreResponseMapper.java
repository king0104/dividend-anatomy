package com.dividendanatomy.web.safety;

import com.dividendanatomy.domain.safety.DividendSafetyScoreResult;
import com.dividendanatomy.service.safety.DividendSafetyScoreServiceResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 반올림은 이 지점에서 딱 한 번만 일어난다 (TimeMachineSimulationResponseMapper와 동일한 관례). */
public final class DividendSafetyScoreResponseMapper {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DividendSafetyScoreResponseMapper() {
    }

    public static DividendSafetyScoreResponse toResponse(DividendSafetyScoreServiceResult serviceResult) {
        if (!serviceResult.available()) {
            return new DividendSafetyScoreResponse(
                    serviceResult.tickerSymbol(), false, null, null, null, null);
        }

        DividendSafetyScoreResult r = serviceResult.result();
        List<DividendSafetyScoreResponse.SafetyIndicatorDto> indicators = List.of(
                indicator("PAYOUT_RATIO", asPercent(r.payoutRatio()), r.payoutSubScore()),
                indicator("FCF_PAYOUT_RATIO", asPercent(r.fcfPayoutRatio()), r.fcfPayoutSubScore()),
                indicator("ROE", asPercent(r.returnOnEquity()), r.returnOnEquitySubScore()),
                indicator("DEBT_TO_EQUITY", round(r.debtToEquity()), r.debtToEquitySubScore()),
                indicator("INTEREST_COVERAGE", round(r.interestCoverage()), r.interestCoverageSubScore()));

        return new DividendSafetyScoreResponse(
                serviceResult.tickerSymbol(),
                true,
                serviceResult.fiscalDateEnding(),
                indicators,
                r.totalScore().setScale(0, RoundingMode.HALF_UP),
                r.band().name());
    }

    private static DividendSafetyScoreResponse.SafetyIndicatorDto indicator(
            String name, BigDecimal value, BigDecimal subScore) {
        return new DividendSafetyScoreResponse.SafetyIndicatorDto(name, value, round(subScore));
    }

    private static BigDecimal asPercent(BigDecimal ratio) {
        return round(ratio.multiply(HUNDRED));
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
