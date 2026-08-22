package com.dividendanatomy.domain.yield;

import java.math.BigDecimal;

/**
 * 배당수익률 변화(ΔY)를 가격 기여도와 배당 기여도로 쪼갠 결과.
 * 값은 반올림하지 않은 원시 비율이다 (예: 1.5%p면 0.015). %p 변환과
 * 소수 자리 반올림은 화면/API 표현 계층의 책임.
 */
public record YieldContribution(BigDecimal priceContribution, BigDecimal dividendContribution) {
}
