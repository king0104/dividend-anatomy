package com.dividendanatomy.web.dividenddisclosure;

import java.util.List;

public record SpecialDividendDisclosureResponse(
        String tickerSymbol,
        int regularCount,
        int specialCount,
        List<DisclosureEntryResponse> entries) {
}
