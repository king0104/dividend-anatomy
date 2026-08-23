package com.dividendanatomy.web.tax;

import java.util.List;

public record NetDividendSummaryResponse(
        String tickerSymbol,
        String taxNotice,
        List<NetDividendEntryResponse> entries) {
}
