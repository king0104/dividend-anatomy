package com.dividendanatomy.service.timemachine;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.ExchangeRate;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.timemachine.InvestMode;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.ExchangeRateRepository;
import com.dividendanatomy.repository.PriceBarRepository;
import com.dividendanatomy.repository.SplitEventRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class TimeMachineSimulationServiceTest {

    @Autowired
    private TickerRepository tickerRepository;
    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;
    @Autowired
    private PriceBarRepository priceBarRepository;
    @Autowired
    private SplitEventRepository splitEventRepository;
    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    private TimeMachineSimulationService service() {
        return new TimeMachineSimulationService(
                tickerRepository, dividendPaymentRepository, priceBarRepository, splitEventRepository, exchangeRateRepository);
    }

    /**
     * 손계산 (가격 100 고정, 배당 2 고정, 2년, 일시불 10만원, 환율 1000):
     * principalUsd = 100,000 / 1000 = 100
     * sharesReinvest_0 = 100/100 = 1, sharesNoReinvest_0 = 1
     * 1년차: dividendReceived = 1*2 = 2 -> newShares = 2/100 = 0.02 -> sharesReinvest = 1.02
     *        cashNoReinvest += 1*2 = 2
     * 2년차: dividendReceived = 1.02*2 = 2.04 -> newShares = 2.04/100 = 0.0204 -> sharesReinvest = 1.0404
     *        cashNoReinvest += 1*2 = 2 (합 4)
     * finalValueReinvestUsd = 1.0404*100 = 104.04, finalValueNoReinvestUsd = 100+4 = 104
     * 환율 1000 적용: finalValueReinvestKrw = 104040, finalValueNoReinvestKrw = 104000, differenceKrw = 40
     */
    @Test
    void handCalculated_twoYearLumpSum() {
        Ticker ticker = tickerRepository.save(new Ticker("TMTEST", "Time Machine Test Co", "USD"));

        LocalDate asOf = LocalDate.parse("2026-08-25");
        LocalDate start = asOf.minusYears(2);
        LocalDate year1End = asOf.minusYears(1);

        priceBarRepository.save(new PriceBar(ticker, start, bd("100"), DataSource.TWELVE_DATA));
        priceBarRepository.save(new PriceBar(ticker, year1End, bd("100"), DataSource.TWELVE_DATA));
        priceBarRepository.save(new PriceBar(ticker, asOf, bd("100"), DataSource.TWELVE_DATA));

        saveDividend(ticker, start.plusMonths(9), "2");
        saveDividend(ticker, year1End.plusMonths(9), "2");

        exchangeRateRepository.save(new ExchangeRate("USD", "KRW", asOf, bd("1000"), DataSource.TWELVE_DATA));

        TimeMachineSimulationResult result =
                service().simulate("TMTEST", InvestMode.LUMP_SUM, bd("100000"), 2, asOf);

        assertEquals(2, result.requestedPeriodYears());
        assertEquals(2, result.actualPeriodYears());
        assertTrue(result.dataComplete());
        assertBigDecimalEquals(bd("104.04"), result.usdResult().finalValueReinvestUsd());
        assertBigDecimalEquals(bd("104"), result.usdResult().finalValueNoReinvestUsd());
        assertBigDecimalEquals(bd("104040"), result.finalValueReinvestKrw());
        assertBigDecimalEquals(bd("104000"), result.finalValueNoReinvestKrw());
        assertBigDecimalEquals(bd("40"), result.differenceKrw());
    }

    /** 요청은 5년인데 실제 가격 이력이 2년치뿐이면, 2년까지만 계산하고 데이터 불완전으로 표시해야 한다. */
    @Test
    void shorterHistoryThanRequested_marksDataIncomplete() {
        Ticker ticker = tickerRepository.save(new Ticker("YOUNG2", "Young Co 2", "USD"));

        LocalDate asOf = LocalDate.parse("2026-08-25");
        LocalDate onlyAvailableStart = asOf.minusYears(2);

        priceBarRepository.save(new PriceBar(ticker, onlyAvailableStart, bd("50"), DataSource.TWELVE_DATA));
        priceBarRepository.save(new PriceBar(ticker, asOf, bd("60"), DataSource.TWELVE_DATA));
        exchangeRateRepository.save(new ExchangeRate("USD", "KRW", asOf, bd("1000"), DataSource.TWELVE_DATA));

        TimeMachineSimulationResult result =
                service().simulate("YOUNG2", InvestMode.LUMP_SUM, bd("100000"), 5, asOf);

        assertEquals(5, result.requestedPeriodYears());
        assertEquals(2, result.actualPeriodYears());
        assertFalse(result.dataComplete());
    }

    @Test
    void throwsWhenTickerUnknown() {
        assertThrows(NoSuchElementException.class,
                () -> service().simulate("NOPE", InvestMode.LUMP_SUM, bd("100000"), 2, LocalDate.parse("2026-08-25")));
    }

    @Test
    void throwsWhenNoPriceDataAtAll() {
        tickerRepository.save(new Ticker("NOPRICE", "No Price Co", "USD"));
        assertThrows(NoSuchElementException.class,
                () -> service().simulate("NOPRICE", InvestMode.LUMP_SUM, bd("100000"), 2, LocalDate.parse("2026-08-25")));
    }

    @Test
    void throwsWhenExchangeRateMissing() {
        Ticker ticker = tickerRepository.save(new Ticker("NOFX", "No FX Co", "USD"));
        LocalDate asOf = LocalDate.parse("2026-08-25");
        priceBarRepository.save(new PriceBar(ticker, asOf.minusYears(2), bd("100"), DataSource.TWELVE_DATA));
        priceBarRepository.save(new PriceBar(ticker, asOf, bd("100"), DataSource.TWELVE_DATA));

        assertThrows(IllegalStateException.class,
                () -> service().simulate("NOFX", InvestMode.LUMP_SUM, bd("100000"), 2, asOf));
    }

    private void saveDividend(Ticker ticker, LocalDate exDividendDate, String amount) {
        dividendPaymentRepository.save(new DividendPayment(
                ticker, exDividendDate, exDividendDate, exDividendDate, bd(amount), DividendType.REGULAR, DataSource.MASSIVE));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected=%s actual=%s".formatted(expected, actual));
    }
}
