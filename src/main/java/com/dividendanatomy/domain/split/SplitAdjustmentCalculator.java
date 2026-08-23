package com.dividendanatomy.domain.split;

import com.dividendanatomy.domain.market.SplitEvent;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * raw 금액을 분할 비율의 누적곱으로 나눠 "현재 주식 수" 기준으로
 * 환산한다 (docs/decisions/03-split-adjustment.md). 원래
 * TtmDividendAggregationService의 private 메서드였으나, 연속 배당
 * 증가 연수 계산([[docs/specs/ticker-summary-metrics.md]])도 같은
 * 조정이 필요해서 공유 유틸리티로 추출했다 — 분할 조정은 이미 한 번
 * 버그가 났던 지점이라 복제하지 않는다.
 */
public final class SplitAdjustmentCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;

    private SplitAdjustmentCalculator() {
    }

    public static BigDecimal adjustedAmount(List<SplitEvent> laterSplits, BigDecimal rawAmount) {
        if (laterSplits.isEmpty()) {
            return rawAmount;
        }
        BigDecimal cumulativeRatio = BigDecimal.ONE;
        for (SplitEvent split : laterSplits) {
            cumulativeRatio = cumulativeRatio.multiply(split.getRatio(), MC);
        }
        return rawAmount.divide(cumulativeRatio, MC);
    }
}
