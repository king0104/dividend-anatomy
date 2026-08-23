package com.dividendanatomy.domain.growth;

/**
 * 최근 3년 CAGR과 10년 CAGR 비교 결과 분류.
 * docs/specs/dividend-growth-deceleration.md 1.4절.
 */
public enum GrowthDecelerationStatus {
    DECELERATING,
    NOT_DECELERATING,
    INSUFFICIENT_DATA
}
