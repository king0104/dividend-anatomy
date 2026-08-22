package com.dividendanatomy.domain.dividendcut;

/**
 * 인접한 두 정기 배당 지급 시점의 TTM 합계를 비교한 결과 분류.
 * docs/specs/dividend-cut-detection.md 1.3절.
 */
public enum CutComparisonStatus {
    CUT,
    NORMAL,
    INCOMPLETE
}
