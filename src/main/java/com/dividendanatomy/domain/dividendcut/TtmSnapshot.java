package com.dividendanatomy.domain.dividendcut;

import com.dividendanatomy.domain.yield.TtmDividendSummary;

import java.time.LocalDate;

/**
 * 특정 정기 배당 지급일을 창 끝으로 하는 TTM 집계 스냅샷.
 * DividendCutDetector가 인접한 두 스냅샷을 비교한다.
 */
public record TtmSnapshot(LocalDate asOf, TtmDividendSummary summary) {
}
