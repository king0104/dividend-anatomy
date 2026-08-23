package com.dividendanatomy.web.fx;

import com.dividendanatomy.domain.fx.KrwConvertedEntry;

import java.util.List;

public final class KrwDividendConversionResponseMapper {

    private KrwDividendConversionResponseMapper() {
    }

    public static KrwDividendConversionResponse toResponse(String symbol, List<KrwConvertedEntry> entries) {
        return new KrwDividendConversionResponse(
                symbol, entries.stream().map(KrwDividendConversionResponseMapper::toEntryResponse).toList());
    }

    private static KrwConvertedEntryResponse toEntryResponse(KrwConvertedEntry entry) {
        return new KrwConvertedEntryResponse(
                entry.exDividendDate(), entry.grossAmountUsd(), entry.netAmountUsd(),
                entry.status(), entry.exchangeRate(), entry.grossAmountKrw(), entry.netAmountKrw());
    }
}
