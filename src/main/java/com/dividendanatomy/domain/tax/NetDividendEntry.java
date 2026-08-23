package com.dividendanatomy.domain.tax;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 미국 원천징수 15% 적용 후 실수령액. 정기/특별배당 구분 없이 전부
 * 과세 대상이다 — IRS 원천징수는 배당 유형을 구분하지 않는다
 * (docs/specs/us-withholding-tax.md 0절).
 */
public record NetDividendEntry(
        LocalDate exDividendDate,
        BigDecimal grossAmount,
        BigDecimal netAmount,
        DividendType type) {

    public static final BigDecimal US_WITHHOLDING_TAX_RATE = new BigDecimal("0.15");
    private static final MathContext MC = MathContext.DECIMAL64;

    public static NetDividendEntry from(DividendPayment payment) {
        BigDecimal net = payment.getAmount()
                .multiply(BigDecimal.ONE.subtract(US_WITHHOLDING_TAX_RATE), MC)
                .setScale(2, RoundingMode.HALF_UP);
        return new NetDividendEntry(payment.getExDividendDate(), payment.getAmount(), net, payment.getType());
    }
}
