package com.dividendanatomy.domain.tax;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetDividendEntryTest {

    private static final Ticker TICKER = new Ticker("KO", "Coca-Cola", "USD");

    @Test
    void appliesFifteenPercentWithholdingExactly() {
        DividendPayment payment = payment(new BigDecimal("1.00"), DividendType.REGULAR);

        NetDividendEntry entry = NetDividendEntry.from(payment);

        assertEquals(0, new BigDecimal("1.00").compareTo(entry.grossAmount()));
        assertEquals(0, new BigDecimal("0.85").compareTo(entry.netAmount()));
    }

    @Test
    void roundsToTwoDecimalsHalfUpForRealKoAmount() {
        // 0.485 * 0.85 = 0.41225 -> HALF_UP 반올림 -> 0.41
        DividendPayment payment = payment(new BigDecimal("0.485"), DividendType.REGULAR);

        NetDividendEntry entry = NetDividendEntry.from(payment);

        assertEquals(0, new BigDecimal("0.41").compareTo(entry.netAmount()));
    }

    @Test
    void appliesWithholdingToSpecialDividendsToo() {
        DividendPayment payment = payment(new BigDecimal("15"), DividendType.SPECIAL);

        NetDividendEntry entry = NetDividendEntry.from(payment);

        assertEquals(DividendType.SPECIAL, entry.type());
        assertEquals(0, new BigDecimal("12.75").compareTo(entry.netAmount()));
    }

    private static DividendPayment payment(BigDecimal amount, DividendType type) {
        LocalDate date = LocalDate.parse("2026-06-15");
        return new DividendPayment(TICKER, date, date, date, amount, type, DataSource.MASSIVE);
    }
}
