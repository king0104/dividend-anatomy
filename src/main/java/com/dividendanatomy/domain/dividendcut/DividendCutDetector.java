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

            // 완전한 창끼리는 actualSum이 아니라 annualizedSum으로 비교한다 — 실제
            // 배당 캘린더는 91.25일 간격이 아니라서 창마다 지급 횟수가 4~5회로
            // 자연스럽게 오갈 수 있고(캘린더 드리프트), raw 합계를 그대로 비교하면
            // 회사가 배당을 바꾸지 않았는데도 가짜 삭감/가짜 성장 신호가 생긴다
            // (docs/decisions/05-ttm-window-boundary-fix.md 참고).
            BigDecimal prevAnnualized = prev.annualizedSum();
            BigDecimal currAnnualized = curr.annualizedSum();

            if (currAnnualized.compareTo(prevAnnualized) < 0) {
                BigDecimal decreasePercent = prevAnnualized.subtract(currAnnualized, MC)
                        .divide(prevAnnualized, MC)
                        .multiply(BigDecimal.valueOf(100), MC);
                results.add(new CutComparisonResult(
                        detectedAt, CutComparisonStatus.CUT,
                        prevAnnualized, currAnnualized, decreasePercent));
            } else {
                results.add(new CutComparisonResult(
                        detectedAt, CutComparisonStatus.NORMAL,
                        prevAnnualized, currAnnualized, null));
            }
        }

        return results;
    }
}
