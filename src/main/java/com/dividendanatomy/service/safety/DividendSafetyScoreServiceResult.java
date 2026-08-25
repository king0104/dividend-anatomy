package com.dividendanatomy.service.safety;

import com.dividendanatomy.domain.safety.DividendSafetyScoreResult;

import java.time.LocalDate;

/**
 * available=false면 result/fiscalDateEnding은 null이다 — 5개 원자재 값 중
 * 하나라도 없거나(계산이 불가능한) 값이면 점수 자체를 계산하지 않는다
 * (CLAUDE.md 데이터 불완전 원칙, docs/decisions/14).
 */
public record DividendSafetyScoreServiceResult(
        String tickerSymbol, boolean available, LocalDate fiscalDateEnding, DividendSafetyScoreResult result) {
}
