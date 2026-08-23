package com.dividendanatomy.domain.dividenddisclosure;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialDividendDisclosureTest {

    private static final Ticker TICKER = new Ticker("COST", "Costco", "USD");

    @Test
    void separatesRegularAndSpecialAndExposesRawAmounts() {
        DividendPayment regular1 = payment(LocalDate.parse("2023-08-24"), new BigDecimal("1.02"), DividendType.REGULAR);
        DividendPayment special = payment(LocalDate.parse("2023-12-27"), new BigDecimal("15"), DividendType.SPECIAL);
        DividendPayment regular2 = payment(LocalDate.parse("2024-02-01"), new BigDecimal("1.02"), DividendType.REGULAR);

        SpecialDividendDisclosure disclosure = SpecialDividendDisclosure.from(List.of(regular1, special, regular2));

        assertEquals(2, disclosure.regularCount());
        assertEquals(1, disclosure.specialCount());
        assertEquals(3, disclosure.entries().size());

        DisclosureEntry specialEntry = disclosure.entries().get(1);
        assertTrue(specialEntry.excluded());
        assertEquals(0, new BigDecimal("15").compareTo(specialEntry.amount()));
        assertEquals("데이터 제공자(Massive) 분류 기준상 특별배당 — 정기 배당 지표 계산에서 제외됨", specialEntry.exclusionReason());

        DisclosureEntry regularEntry = disclosure.entries().get(0);
        assertFalse(regularEntry.excluded());
        assertNull(regularEntry.exclusionReason());
        assertEquals(0, new BigDecimal("1.02").compareTo(regularEntry.amount()));
    }

    @Test
    void returnsZeroCountsForEmptyHistory() {
        SpecialDividendDisclosure disclosure = SpecialDividendDisclosure.from(List.of());

        assertEquals(0, disclosure.regularCount());
        assertEquals(0, disclosure.specialCount());
        assertTrue(disclosure.entries().isEmpty());
    }

    private static DividendPayment payment(LocalDate exDividendDate, BigDecimal amount, DividendType type) {
        return new DividendPayment(TICKER, exDividendDate, exDividendDate, exDividendDate, amount, type, DataSource.MASSIVE);
    }
}
