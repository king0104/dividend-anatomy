package com.dividendanatomy.domain.ticker;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 역년(calendar year) 단위 총 정기배당을 비교해 "몇 년 연속 인상"인지
 * 계산한다. 진행 중인 올해(currentYear)는 항상 제외한다 — 아직 안 끝난
 * 해의 부분 합계를 완결된 작년과 비교하면 가짜 감소로 오판할 수 있다
 * (docs/specs/ticker-summary-metrics.md 0절).
 */
public final class DividendIncreaseStreakCalculator {

    private DividendIncreaseStreakCalculator() {
    }

    public static DividendIncreaseStreakResult evaluate(
            Map<Integer, BigDecimal> annualTotalsByYear,
            Map<Integer, Integer> paymentCountByYear,
            int expectedCount,
            int currentYear) {
        List<Integer> completeYears = annualTotalsByYear.keySet().stream()
                .filter(y -> y < currentYear)
                .sorted()
                .toList();

        if (completeYears.size() < 2) {
            return new DividendIncreaseStreakResult(DividendIncreaseStreakStatus.INSUFFICIENT_DATA, 0);
        }

        int streak = 0;
        int year = completeYears.get(completeYears.size() - 1);
        while (true) {
            if (paymentCountByYear.getOrDefault(year, 0) < expectedCount) {
                break;
            }
            int prevYear = year - 1;
            if (!annualTotalsByYear.containsKey(prevYear) || paymentCountByYear.getOrDefault(prevYear, 0) < expectedCount) {
                break;
            }
            if (annualTotalsByYear.get(year).compareTo(annualTotalsByYear.get(prevYear)) > 0) {
                streak++;
                year = prevYear;
            } else {
                break;
            }
        }

        return new DividendIncreaseStreakResult(DividendIncreaseStreakStatus.CALCULATED, streak);
    }
}
