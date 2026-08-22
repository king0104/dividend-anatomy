package com.dividendanatomy.domain.dividendcut;

import com.dividendanatomy.domain.yield.TtmDividendSummary;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 정기 배당 지급 시점별 TTM 스냅샷을 순서대로 받아, 인접한 두 시점의
 * TTM 합계를 비교해 삭감 여부를 판정한다. raw 지급액이 아니라 TTM
 * 합계(분할 조정 완료)를 비교하는 이유는
 * docs/specs/dividend-cut-detection.md 1.1절 참고.
 */
public final class DividendCutDetector {

    private static final MathContext MC = MathContext.DECIMAL64;

    public List<CutComparisonResult> detect(List<TtmSnapshot> orderedSnapshots) {
        List<CutComparisonResult> results = new ArrayList<>();

        for (int i = 1; i < orderedSnapshots.size(); i++) {
            TtmDividendSummary prev = orderedSnapshots.get(i - 1).summary();
            TtmDividendSummary curr = orderedSnapshots.get(i).summary();
            var detectedAt = orderedSnapshots.get(i).asOf();

            if (!prev.isComplete() || !curr.isComplete()) {
                results.add(new CutComparisonResult(
                        detectedAt, CutComparisonStatus.INCOMPLETE,
                        prev.actualSum(), curr.actualSum(), null));
                continue;
            }

            if (curr.actualSum().compareTo(prev.actualSum()) < 0) {
                BigDecimal decreasePercent = prev.actualSum().subtract(curr.actualSum(), MC)
                        .divide(prev.actualSum(), MC)
                        .multiply(BigDecimal.valueOf(100), MC);
                results.add(new CutComparisonResult(
                        detectedAt, CutComparisonStatus.CUT,
                        prev.actualSum(), curr.actualSum(), decreasePercent));
            } else {
                results.add(new CutComparisonResult(
                        detectedAt, CutComparisonStatus.NORMAL,
                        prev.actualSum(), curr.actualSum(), null));
            }
        }

        return results;
    }
}
