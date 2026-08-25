package com.dividendanatomy.service.safety;

import com.dividendanatomy.domain.fundamentals.FinancialFundamentals;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.safety.SafetyBand;
import com.dividendanatomy.repository.FinancialFundamentalsRepository;
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
class DividendSafetyScoreServiceTest {

    @Autowired
    private TickerRepository tickerRepository;
    @Autowired
    private FinancialFundamentalsRepository financialFundamentalsRepository;

    private DividendSafetyScoreService service() {
        return new DividendSafetyScoreService(tickerRepository, financialFundamentalsRepository);
    }

    /**
     * domain/safety/DividendSafetyScoreCalculatorTest의 handCalculated_mixedIndicators와
     * 같은 값이 나오도록 원자재 값을 골랐다: 배당성향=62.5/100=0.625,
     * FCF대비배당=165.7/(100-0)=1.657, ROE=0.42(직접 제공), D/E=150/100=1.5,
     * 이자보상배율=1067/100=10.67 -> 총점 68.75 -> GREEN.
     */
    @Test
    void handCalculated_allFieldsPresent_matchesDomainCalculatorTest() {
        Ticker ticker = tickerRepository.save(new Ticker("SAFETYTEST", "Safety Test Co", "USD"));
        LocalDate fiscalDate = LocalDate.parse("2025-12-31");

        FinancialFundamentals f = new FinancialFundamentals(ticker, fiscalDate, DataSource.ALPHA_VANTAGE);
        f.update(
                fiscalDate,
                bd("0.42"),      // returnOnEquity
                bd("62.5"),      // dividendPerShare
                bd("100"),       // eps
                bd("165.7"),     // dividendPayout
                bd("100"),       // operatingCashflow
                bd("0"),         // capitalExpenditures
                bd("150"),       // shortLongTermDebtTotal
                bd("100"),       // totalShareholderEquity
                bd("1067"),      // ebit
                bd("100"),       // interestExpense
                DataSource.ALPHA_VANTAGE);
        financialFundamentalsRepository.save(f);

        DividendSafetyScoreServiceResult result = service().getScore("SAFETYTEST");

        assertTrue(result.available());
        assertEquals(fiscalDate, result.fiscalDateEnding());
        assertBigDecimalEquals(bd("68.75"), result.result().totalScore());
        assertEquals(SafetyBand.GREEN, result.result().band());
    }

    @Test
    void missingOneField_marksUnavailable() {
        Ticker ticker = tickerRepository.save(new Ticker("MISSING1", "Missing Field Co", "USD"));
        LocalDate fiscalDate = LocalDate.parse("2025-12-31");

        FinancialFundamentals f = new FinancialFundamentals(ticker, fiscalDate, DataSource.ALPHA_VANTAGE);
        f.update(
                fiscalDate,
                bd("0.42"), bd("62.5"), bd("100"), bd("165.7"), bd("100"), bd("0"),
                bd("150"), bd("100"), bd("1067"),
                null, // interestExpense 결측
                DataSource.ALPHA_VANTAGE);
        financialFundamentalsRepository.save(f);

        DividendSafetyScoreServiceResult result = service().getScore("MISSING1");

        assertFalse(result.available());
        assertEquals(null, result.result());
    }

    @Test
    void nonPositiveEps_marksUnavailable() {
        Ticker ticker = tickerRepository.save(new Ticker("LOSSCO", "Loss Making Co", "USD"));
        LocalDate fiscalDate = LocalDate.parse("2025-12-31");

        FinancialFundamentals f = new FinancialFundamentals(ticker, fiscalDate, DataSource.ALPHA_VANTAGE);
        f.update(
                fiscalDate,
                bd("0.42"), bd("62.5"),
                bd("-10"), // eps 적자
                bd("165.7"), bd("100"), bd("0"), bd("150"), bd("100"), bd("1067"), bd("100"),
                DataSource.ALPHA_VANTAGE);
        financialFundamentalsRepository.save(f);

        DividendSafetyScoreServiceResult result = service().getScore("LOSSCO");

        assertFalse(result.available());
    }

    @Test
    void noFundamentalsRowAtAll_marksUnavailable() {
        tickerRepository.save(new Ticker("NOFUND", "No Fundamentals Co", "USD"));

        DividendSafetyScoreServiceResult result = service().getScore("NOFUND");

        assertFalse(result.available());
    }

    @Test
    void throwsWhenTickerUnknown() {
        assertThrows(NoSuchElementException.class, () -> service().getScore("NOPE"));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected=%s actual=%s".formatted(expected, actual));
    }
}
