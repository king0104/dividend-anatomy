package com.dividendanatomy.domain.safety;

import java.math.BigDecimal;

/**
 * 5개 지표 각각의 원래 비율값과 서브스코어(0~20)를 총점·밴드와 함께 끝까지
 * 들고 간다 — 개별 지표도 그 자체로 의미가 있어서(예: "배당성향은 안전한데
 * 부채비율만 나쁨") 합계만 남기고 버리지 않는다. 반올림 없음(web 계층에서만).
 */
public record DividendSafetyScoreResult(
        BigDecimal payoutRatio,
        BigDecimal payoutSubScore,
        BigDecimal fcfPayoutRatio,
        BigDecimal fcfPayoutSubScore,
        BigDecimal returnOnEquity,
        BigDecimal returnOnEquitySubScore,
        BigDecimal debtToEquity,
        BigDecimal debtToEquitySubScore,
        BigDecimal interestCoverage,
        BigDecimal interestCoverageSubScore,
        BigDecimal totalScore,
        SafetyBand band) {
}
