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

    /**
     * foundCount는 expectedCount를 넘을 수 있다 — 실제 배당 캘린더는 정확히
     * 91.25일 간격이 아니라서, 롤링 12개월 창에 분기 배당이 5번 들어가는 경우가
     * 실제로 있다 (KO 실데이터로 확인, docs/decisions/05-ttm-window-boundary-fix.md
     * "추가 발견" 참고). 이걸 예외로 막으면 정상적인 실데이터에서도 계산이
     * 죽는다 — 캘린더 드리프트는 버그가 아니라 실제 배당 캘린더의 특성이다.
     */
    public TtmDividendSummary {
        if (expectedCount <= 0) {
            throw new IllegalArgumentException("expectedCount(%d)는 1 이상이어야 한다".formatted(expectedCount));
        }
        if (foundCount < 0) {
            throw new IllegalArgumentException("foundCount(%d)는 0 이상이어야 한다".formatted(foundCount));
        }
        if (foundCount == 0 && annualizedSum != null) {
            throw new IllegalArgumentException("foundCount가 0이면 annualizedSum은 null이어야 한다");
        }
    }

    /** foundCount가 expectedCount 이상이면 완전 — 초과(캘린더 드리프트로 인한 여분)는 허용, 미달(구멍)만 불완전. */
    public boolean isComplete() {
        return foundCount >= expectedCount;
    }
}
