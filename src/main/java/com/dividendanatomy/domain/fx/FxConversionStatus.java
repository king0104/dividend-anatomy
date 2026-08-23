package com.dividendanatomy.domain.fx;

/**
 * PAY_DATE_MISSING과 NO_RATE_DATA_AVAILABLE을 서로 다른 상태로
 * 구분한다 — 둘 다 "환산 불가"라는 결과는 같지만 원인이 다르고,
 * 조용히 하나로 뭉뚱그리지 않는다 (docs/specs/krw-dividend-conversion.md 4절).
 */
public enum FxConversionStatus {
    CONVERTED,
    PAY_DATE_MISSING,
    NO_RATE_DATA_AVAILABLE
}
