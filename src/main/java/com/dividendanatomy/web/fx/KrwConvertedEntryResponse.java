package com.dividendanatomy.web.fx;

import com.dividendanatomy.domain.fx.FxConversionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KrwConvertedEntryResponse(
        LocalDate exDividendDate,
        BigDecimal grossAmountUsd,
        BigDecimal netAmountUsd,
        FxConversionStatus status,
        BigDecimal exchangeRate,
        BigDecimal grossAmountKrw,
        BigDecimal netAmountKrw) {
}
