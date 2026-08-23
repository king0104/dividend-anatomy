package com.dividendanatomy.web.tax;

import com.dividendanatomy.domain.tax.NetDividendEntry;
import com.dividendanatomy.domain.tax.NetDividendSummary;

public final class UsWithholdingTaxResponseMapper {

    private UsWithholdingTaxResponseMapper() {
    }

    public static NetDividendSummaryResponse toResponse(String symbol, NetDividendSummary summary) {
        return new NetDividendSummaryResponse(
                symbol,
                NetDividendSummary.TAX_NOTICE,
                summary.entries().stream().map(UsWithholdingTaxResponseMapper::toEntryResponse).toList());
    }

    private static NetDividendEntryResponse toEntryResponse(NetDividendEntry entry) {
        return new NetDividendEntryResponse(entry.exDividendDate(), entry.grossAmount(), entry.netAmount(), entry.type());
    }
}
