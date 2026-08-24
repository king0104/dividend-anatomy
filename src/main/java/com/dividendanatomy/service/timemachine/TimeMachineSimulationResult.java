package com.dividendanatomy.service.timemachine;

import com.dividendanatomy.domain.timemachine.TimeMachineResult;

import java.math.BigDecimal;

/**
 * 서비스 계층 결과. USD 계산 결과에 원화 환산과 데이터 완전성 정보를
 * 덧붙인다. 반올림 없음 — web 계층 ResponseMapper에서만 반올림한다.
 */
public record TimeMachineSimulationResult(
        String tickerSymbol,
        int requestedPeriodYears,
        int actualPeriodYears,
        boolean dataComplete,
        TimeMachineResult usdResult,
        BigDecimal exchangeRateUsdToKrw,
        BigDecimal finalValueReinvestKrw,
        BigDecimal finalValueNoReinvestKrw,
        BigDecimal differenceKrw) {
}
