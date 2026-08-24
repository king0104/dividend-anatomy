package com.dividendanatomy.domain.timemachine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 재투자 시나리오와 미재투자 시나리오를 비교한 결과. 전부 USD 원시값이며
 * 반올림하지 않는다 — 반올림은 web 계층 ResponseMapper에서만 한다
 * (docs/specs 관례, YieldDecompositionResponseMapper 참고).
 */
public record TimeMachineResult(
        BigDecimal finalValueReinvestUsd,
        BigDecimal finalValueNoReinvestUsd,
        BigDecimal differenceUsd,
        BigDecimal totalReturnRatio,
        List<YearlySnapshot> yearlySeries) {

    public record YearlySnapshot(LocalDate checkpointDate, BigDecimal reinvestValueUsd, BigDecimal noReinvestValueUsd) {
    }
}
