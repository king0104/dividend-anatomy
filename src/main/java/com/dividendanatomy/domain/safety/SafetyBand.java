package com.dividendanatomy.domain.safety;

/**
 * 배당 안전도 총점(0~100)을 3색 신호등으로 분류. 경계는 Simply Safe
 * Dividends가 공개한 5단계 경계(0-20/21-40/41-60/61-80/81-100)를 압축한
 * 값이다(docs/decisions/14) — 임의의 반올림이 아니다.
 */
public enum SafetyBand {
    RED,
    YELLOW,
    GREEN
}
