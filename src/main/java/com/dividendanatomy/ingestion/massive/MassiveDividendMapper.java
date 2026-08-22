package com.dividendanatomy.ingestion.massive;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;

import java.time.LocalDate;

/**
 * 순수 변환 — HTTP 없음, DB 없음. dividend_type을 REGULAR/SPECIAL로
 * 매핑한다 (docs/decisions/04-dividend-classification.md — COST의 실제
 * $15 특별배당이 dividend_type=SC로 확인됨).
 */
public final class MassiveDividendMapper {

    private MassiveDividendMapper() {
    }

    public static DividendPayment toDividendPayment(MassiveDividend dividend, Ticker ticker) {
        DividendType type = classify(dividend.dividendType());
        return new DividendPayment(
                ticker,
                LocalDate.parse(dividend.exDividendDate()),
                parseOrNull(dividend.recordDate()),
                parseOrNull(dividend.payDate()),
                dividend.cashAmount(),
                type,
                DataSource.MASSIVE);
    }

    private static DividendType classify(String rawType) {
        return switch (rawType) {
            case "CD" -> DividendType.REGULAR;
            case "SC" -> DividendType.SPECIAL;
            default -> throw new IllegalArgumentException(
                    "알 수 없는 dividend_type: '%s' — docs/decisions/04-dividend-classification.md 재검토 필요"
                            .formatted(rawType));
        };
    }

    private static LocalDate parseOrNull(String value) {
        return value == null ? null : LocalDate.parse(value);
    }
}
