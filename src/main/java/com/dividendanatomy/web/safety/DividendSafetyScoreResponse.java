package com.dividendanatomy.web.safety;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * available=false면 fiscalDateEnding/indicators/totalScore/band는 전부
 * null이다. indicators는 5개 지표 각각의 원래 값(퍼센트류는 이미 ×100된
 * 값, D/E·이자보상배율은 배수 그대로)과 서브스코어(0~20)를 담는다 —
 * 기획서의 "1단계: 총점만 / 2단계: 지표별로 풀어서 노출"을 API 한 번으로
 * 지원하기 위함(docs/decisions/14).
 */
public record DividendSafetyScoreResponse(
        String tickerSymbol,
        boolean available,
        LocalDate fiscalDateEnding,
        List<SafetyIndicatorDto> indicators,
        BigDecimal totalScore,
        String band) {

    public record SafetyIndicatorDto(String name, BigDecimal value, BigDecimal subScore) {
    }
}
