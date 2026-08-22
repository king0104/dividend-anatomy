package com.dividendanatomy.web.dividendcut;

import com.dividendanatomy.domain.dividendcut.CutComparisonResult;

import java.math.RoundingMode;
import java.util.List;

/**
 * decreasePercent만 응답 시점에 반올림한다(scale=2, HALF_UP) — 원본 TTM
 * 금액은 %가 아니라 그대로 노출한다 (docs/specs/dividend-cut-detection.md 3절).
 */
public final class DividendCutResponseMapper {

    private DividendCutResponseMapper() {
    }

    public static DividendCutResponse toResponse(String symbol, List<CutComparisonResult> results) {
        List<DividendCutResponse.CutEventDto> dtos = results.stream()
                .map(r -> new DividendCutResponse.CutEventDto(
                        r.detectedAt(),
                        r.status(),
                        r.previousTtmAmount(),
                        r.currentTtmAmount(),
                        r.decreasePercent() == null ? null : r.decreasePercent().setScale(2, RoundingMode.HALF_UP)))
                .toList();
        return new DividendCutResponse(symbol, dtos);
    }
}
