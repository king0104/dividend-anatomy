package com.dividendanatomy.domain.fx;

import com.dividendanatomy.domain.market.ExchangeRate;
import com.dividendanatomy.domain.tax.NetDividendEntry;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * NetDividendEntry(세후 USD, 이미 반올림된 최종값)에 "배당 지급일(payDate)
 * 기준" 환율을 곱해 원화 환산한다. payDate 누락과 환율 데이터 없음을
 * 서로 다른 상태로 구분한다 (docs/specs/krw-dividend-conversion.md 4절).
 */
public record KrwConvertedEntry(
        LocalDate exDividendDate,
        BigDecimal grossAmountUsd,
        BigDecimal netAmountUsd,
        FxConversionStatus status,
        BigDecimal exchangeRate,
        BigDecimal grossAmountKrw,
        BigDecimal netAmountKrw) {

    private static final MathContext MC = MathContext.DECIMAL64;

    public static KrwConvertedEntry convert(NetDividendEntry usdEntry, LocalDate payDate, Optional<ExchangeRate> rate) {
        if (payDate == null) {
            return unavailable(usdEntry, FxConversionStatus.PAY_DATE_MISSING);
        }
        if (rate.isEmpty()) {
            return unavailable(usdEntry, FxConversionStatus.NO_RATE_DATA_AVAILABLE);
        }

        BigDecimal r = rate.get().getRate();
        BigDecimal grossKrw = usdEntry.grossAmount().multiply(r, MC).setScale(0, RoundingMode.HALF_UP);
        BigDecimal netKrw = usdEntry.netAmount().multiply(r, MC).setScale(0, RoundingMode.HALF_UP);
        return new KrwConvertedEntry(
                usdEntry.exDividendDate(), usdEntry.grossAmount(), usdEntry.netAmount(),
                FxConversionStatus.CONVERTED, r, grossKrw, netKrw);
    }

    private static KrwConvertedEntry unavailable(NetDividendEntry usdEntry, FxConversionStatus status) {
        return new KrwConvertedEntry(
                usdEntry.exDividendDate(), usdEntry.grossAmount(), usdEntry.netAmount(), status, null, null, null);
    }
}
