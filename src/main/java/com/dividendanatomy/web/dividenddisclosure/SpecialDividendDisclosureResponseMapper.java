package com.dividendanatomy.web.dividenddisclosure;

import com.dividendanatomy.domain.dividenddisclosure.DisclosureEntry;
import com.dividendanatomy.domain.dividenddisclosure.SpecialDividendDisclosure;

public final class SpecialDividendDisclosureResponseMapper {

    private SpecialDividendDisclosureResponseMapper() {
    }

    public static SpecialDividendDisclosureResponse toResponse(String symbol, SpecialDividendDisclosure disclosure) {
        return new SpecialDividendDisclosureResponse(
                symbol,
                disclosure.regularCount(),
                disclosure.specialCount(),
                disclosure.entries().stream().map(SpecialDividendDisclosureResponseMapper::toEntryResponse).toList());
    }

    private static DisclosureEntryResponse toEntryResponse(DisclosureEntry entry) {
        return new DisclosureEntryResponse(
                entry.exDividendDate(), entry.amount(), entry.type(), entry.excluded(), entry.exclusionReason());
    }
}
