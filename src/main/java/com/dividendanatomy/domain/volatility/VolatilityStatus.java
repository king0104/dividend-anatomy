package com.dividendanatomy.domain.volatility;

/**
 * 표본 기간(10년, 11개 TTM 지점) 전체가 완전해야만 표준편차를 계산한다.
 * docs/specs/dividend-volatility.md 4절.
 */
public enum VolatilityStatus {
    COMPLETE,
    INSUFFICIENT_DATA
}
