package com.dividendanatomy.domain.dividenddisclosure;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 지급 건 하나. amount는 DividendPayment 원본 그대로(raw, 분할 미조정) —
 * 이 지표의 목적은 분류 근거 확인이지 정기 지표 계산이 아니므로 재계산하지
 * 않는다 (docs/specs/special-dividend-disclosure.md 1.2).
 */
public record DisclosureEntry(
        LocalDate exDividendDate,
        BigDecimal amount,
        DividendType type,
        boolean excluded,
        String exclusionReason) {

    private static final String SPECIAL_EXCLUSION_REASON =
            "데이터 제공자(Massive) 분류 기준상 특별배당 — 정기 배당 지표 계산에서 제외됨";

    public static DisclosureEntry from(DividendPayment payment) {
        boolean excluded = payment.getType() == DividendType.SPECIAL;
        return new DisclosureEntry(
                payment.getExDividendDate(),
                payment.getAmount(),
                payment.getType(),
                excluded,
                excluded ? SPECIAL_EXCLUSION_REASON : null);
    }
}
