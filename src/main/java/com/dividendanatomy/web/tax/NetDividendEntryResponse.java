package com.dividendanatomy.web.tax;

import com.dividendanatomy.domain.dividend.DividendType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NetDividendEntryResponse(
        LocalDate exDividendDate,
        BigDecimal grossAmount,
        BigDecimal netAmount,
        DividendType type) {
}
