package com.dividendanatomy.domain.tax;

import com.dividendanatomy.domain.dividend.DividendPayment;

import java.util.List;

public record NetDividendSummary(List<NetDividendEntry> entries) {

    public static final String TAX_NOTICE =
            "이 계산은 미국 원천징수 15%까지만 반영합니다. "
                    + "연간 금융소득이 2,000만원을 초과하면 금융소득종합과세 대상이 될 수 있습니다.";

    public static NetDividendSummary from(List<DividendPayment> payments) {
        return new NetDividendSummary(payments.stream().map(NetDividendEntry::from).toList());
    }
}
