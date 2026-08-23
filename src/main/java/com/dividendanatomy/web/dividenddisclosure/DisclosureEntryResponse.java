package com.dividendanatomy.web.dividenddisclosure;

import com.dividendanatomy.domain.dividend.DividendType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** amount는 반올림 없이 DB 원본 그대로 노출한다 (계산 로직 없는 순수 조회). */
public record DisclosureEntryResponse(
        LocalDate exDividendDate,
        BigDecimal amount,
        DividendType type,
        boolean excluded,
        String exclusionReason) {
}
