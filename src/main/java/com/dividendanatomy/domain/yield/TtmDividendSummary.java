package com.dividendanatomy.domain.yield;

import java.math.BigDecimal;

/**
 * trailing 12개월 정기 배당 합계 집계 결과.
 * DB 조회는 상위 계층 책임이고, 이 레코드는 이미 집계된 숫자만 담는다.
 */
public record TtmDividendSummary(
        BigDecimal actualSum,
        BigDecimal annualizedSum,
        int foundCount,
        int expectedCount) {

    public TtmDividendSummary {
        if (expectedCount <= 0) {
            throw new IllegalArgumentException("expectedCount(%d)는 1 이상이어야 한다".formatted(expectedCount));
        }
        if (foundCount < 0 || foundCount > expectedCount) {
            throw new IllegalArgumentException(
                    "foundCount(%d)는 0 이상 expectedCount(%d) 이하여야 한다".formatted(foundCount, expectedCount));
        }
        if (foundCount == 0 && annualizedSum != null) {
            throw new IllegalArgumentException("foundCount가 0이면 annualizedSum은 null이어야 한다");
        }
    }

    public boolean isComplete() {
        return foundCount == expectedCount;
    }
}
