package com.dividendanatomy.domain.dividendcut;

import com.dividendanatomy.domain.yield.TtmDividendSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DividendCutDetectorTest {

    private final DividendCutDetector detector = new DividendCutDetector();

    /** 손계산: (4.00-3.60)/4.00*100 = 10.00. */
    @Test
    void detectsCutWhenTtmDecreases() {
        TtmSnapshot prev = snapshot("2025-01-01", "4.00", 4, 4);
        TtmSnapshot curr = snapshot("2025-04-01", "3.60", 4, 4);

        List<CutComparisonResult> results = detector.detect(List.of(prev, curr));

        assertEquals(1, results.size());
        CutComparisonResult result = results.get(0);
        assertEquals(LocalDate.parse("2025-04-01"), result.detectedAt());
        assertEquals(CutComparisonStatus.CUT, result.status());
        assertEquals(0, new BigDecimal("10.00").compareTo(result.decreasePercent()));
    }

    @Test
    void classifiesAsNormalWhenTtmIncreases() {
        TtmSnapshot prev = snapshot("2025-01-01", "4.00", 4, 4);
        TtmSnapshot curr = snapshot("2025-04-01", "4.40", 4, 4);

        List<CutComparisonResult> results = detector.detect(List.of(prev, curr));

        assertEquals(1, results.size());
        assertEquals(CutComparisonStatus.NORMAL, results.get(0).status());
        assertNull(results.get(0).decreasePercent());
    }

    /** curr가 수치상 prev보다 작아도, 창이 불완전하면 CUT으로 확정하지 않는다 (예측 #11). */
    @Test
    void classifiesAsIncompleteRatherThanCutWhenEitherWindowIsIncomplete() {
        TtmSnapshot prev = snapshot("2025-01-01", "4.00", 4, 4);
        TtmSnapshot curr = snapshot("2025-04-01", "3.60", 3, 4);

        List<CutComparisonResult> results = detector.detect(List.of(prev, curr));

        assertEquals(1, results.size());
        assertEquals(CutComparisonStatus.INCOMPLETE, results.get(0).status());
        assertNull(results.get(0).decreasePercent());
    }

    /** 비교 대상이 없는 경계값 — 인덱스 예외 없이 빈 결과여야 한다 (예측 #12). */
    @Test
    void returnsEmptyWhenFewerThanTwoSnapshots() {
        assertTrue(detector.detect(List.of()).isEmpty());
        assertTrue(detector.detect(List.of(snapshot("2025-01-01", "4.00", 4, 4))).isEmpty());
    }

    @Test
    void firstSnapshotNeverAppearsAsDetectedAt() {
        TtmSnapshot s1 = snapshot("2025-01-01", "4.00", 4, 4);
        TtmSnapshot s2 = snapshot("2025-04-01", "4.00", 4, 4);
        TtmSnapshot s3 = snapshot("2025-07-01", "3.80", 4, 4);
        TtmSnapshot s4 = snapshot("2025-10-01", "4.20", 4, 4);

        List<CutComparisonResult> results = detector.detect(List.of(s1, s2, s3, s4));

        assertEquals(3, results.size());
        assertEquals(List.of(
                        LocalDate.parse("2025-04-01"),
                        LocalDate.parse("2025-07-01"),
                        LocalDate.parse("2025-10-01")),
                results.stream().map(CutComparisonResult::detectedAt).toList());
        assertEquals(CutComparisonStatus.NORMAL, results.get(0).status());
        assertEquals(CutComparisonStatus.CUT, results.get(1).status());
        assertEquals(CutComparisonStatus.NORMAL, results.get(2).status());
    }

    private TtmSnapshot snapshot(String date, String actualSum, int foundCount, int expectedCount) {
        BigDecimal actual = new BigDecimal(actualSum);
        BigDecimal annualized = foundCount == 0
                ? null
                : actual.multiply(BigDecimal.valueOf(expectedCount))
                        .divide(BigDecimal.valueOf(foundCount), java.math.MathContext.DECIMAL64);
        return new TtmSnapshot(
                LocalDate.parse(date),
                new TtmDividendSummary(actual, annualized, foundCount, expectedCount));
    }
}
