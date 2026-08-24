package com.dividendanatomy.web.timemachine;

import com.dividendanatomy.domain.timemachine.TimeMachineResult;
import com.dividendanatomy.service.timemachine.TimeMachineSimulationResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * TimeMachineSimulationResult(원시 USD/KRW 값, 무반올림) →
 * TimeMachineSimulationResponse(원화 정수, 퍼센트 소수 2자리) 순수 변환.
 * 반올림은 이 지점에서 딱 한 번만 일어난다 (YieldDecompositionResponseMapper와 동일한 관례).
 */
public final class TimeMachineSimulationResponseMapper {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int KRW_SCALE = 0;
    private static final int PERCENT_SCALE = 2;

    private TimeMachineSimulationResponseMapper() {
    }

    public static TimeMachineSimulationResponse toResponse(TimeMachineSimulationResult result) {
        BigDecimal rate = result.exchangeRateUsdToKrw();

        var yearlySeries = result.usdResult().yearlySeries().stream()
                .map(snapshot -> toSnapshotDto(snapshot, rate))
                .toList();

        return new TimeMachineSimulationResponse(
                result.tickerSymbol(),
                result.requestedPeriodYears(),
                result.actualPeriodYears(),
                result.dataComplete(),
                toKrwScale(result.finalValueReinvestKrw()),
                toKrwScale(result.finalValueNoReinvestKrw()),
                toKrwScale(result.differenceKrw()),
                toPercent(result.usdResult().totalReturnRatio()),
                yearlySeries);
    }

    private static TimeMachineSimulationResponse.YearlySnapshotDto toSnapshotDto(
            TimeMachineResult.YearlySnapshot snapshot, BigDecimal rate) {
        return new TimeMachineSimulationResponse.YearlySnapshotDto(
                snapshot.checkpointDate(),
                toKrwScale(snapshot.reinvestValueUsd().multiply(rate, MC)),
                toKrwScale(snapshot.noReinvestValueUsd().multiply(rate, MC)));
    }

    private static BigDecimal toKrwScale(BigDecimal value) {
        return value.setScale(KRW_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal toPercent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
