package com.dividendanatomy.web.fx;

import java.util.List;

public record KrwDividendConversionResponse(
        String tickerSymbol,
        List<KrwConvertedEntryResponse> entries) {
}
