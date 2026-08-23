package com.dividendanatomy.domain.dividenddisclosure;

import com.dividendanatomy.domain.dividend.DividendPayment;

import java.util.List;

public record SpecialDividendDisclosure(
        List<DisclosureEntry> entries,
        int regularCount,
        int specialCount) {

    public static SpecialDividendDisclosure from(List<DividendPayment> payments) {
        List<DisclosureEntry> entries = payments.stream().map(DisclosureEntry::from).toList();
        int specialCount = (int) entries.stream().filter(DisclosureEntry::excluded).count();
        return new SpecialDividendDisclosure(entries, entries.size() - specialCount, specialCount);
    }
}
