package com.dividendanatomy.service.safety;

import com.dividendanatomy.domain.fundamentals.FinancialFundamentals;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.safety.DividendSafetyScoreCalculator;
import com.dividendanatomy.domain.safety.DividendSafetyScoreResult;
import com.dividendanatomy.repository.FinancialFundamentalsRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 티커 심볼을 받아 배당 안전도 스코어를 계산한다. DB만 읽는다 — 외부 API
 * 호출 없음(CLAUDE.md "서비스 계층은 DB만 읽는다").
 *
 * 5개 원자재 값(DPS, EPS, dividendPayout, OCF, Capex, ROE, 이자부담부채,
 * 자기자본, EBIT, 이자비용) 중 하나라도 없거나, 비율 계산 분모가 0 이하라서
 * 경제적으로 의미 없는 값이 나오는 경우(예: 적자로 EPS≤0) 점수 자체를
 * 계산하지 않고 available=false를 반환한다 — 도메인 계산기는 항상 유효한
 * 입력만 받는다는 전제를 지키기 위함이다.
 */
@Service
public class DividendSafetyScoreService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final TickerRepository tickerRepository;
    private final FinancialFundamentalsRepository financialFundamentalsRepository;

    public DividendSafetyScoreService(
            TickerRepository tickerRepository, FinancialFundamentalsRepository financialFundamentalsRepository) {
        this.tickerRepository = tickerRepository;
        this.financialFundamentalsRepository = financialFundamentalsRepository;
    }

    public DividendSafetyScoreServiceResult getScore(String symbol) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NoSuchElementException("알 수 없는 티커: " + symbol));

        Optional<FinancialFundamentals> maybeFundamentals = financialFundamentalsRepository.findByTicker(ticker);
        if (maybeFundamentals.isEmpty()) {
            return unavailable(symbol);
        }
        FinancialFundamentals f = maybeFundamentals.get();

        if (isMissing(f.getDividendPerShare(), f.getEps(), f.getDividendPayout(), f.getOperatingCashflow(),
                f.getCapitalExpenditures(), f.getReturnOnEquity(), f.getShortLongTermDebtTotal(),
                f.getTotalShareholderEquity(), f.getEbit(), f.getInterestExpense())) {
            return unavailable(symbol);
        }

        BigDecimal fcfDenominator = f.getOperatingCashflow().subtract(f.getCapitalExpenditures(), MC);
        if (f.getEps().signum() <= 0
                || fcfDenominator.signum() <= 0
                || f.getTotalShareholderEquity().signum() <= 0
                || f.getInterestExpense().signum() <= 0) {
            return unavailable(symbol);
        }

        BigDecimal payoutRatio = f.getDividendPerShare().divide(f.getEps(), MC);
        BigDecimal fcfPayoutRatio = f.getDividendPayout().divide(fcfDenominator, MC);
        BigDecimal debtToEquity = f.getShortLongTermDebtTotal().divide(f.getTotalShareholderEquity(), MC);
        BigDecimal interestCoverage = f.getEbit().divide(f.getInterestExpense(), MC);

        DividendSafetyScoreResult result = DividendSafetyScoreCalculator.calculate(
                payoutRatio, fcfPayoutRatio, f.getReturnOnEquity(), debtToEquity, interestCoverage);

        return new DividendSafetyScoreServiceResult(symbol, true, f.getFiscalDateEnding(), result);
    }

    private static boolean isMissing(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value == null) {
                return true;
            }
        }
        return false;
    }

    private static DividendSafetyScoreServiceResult unavailable(String symbol) {
        return new DividendSafetyScoreServiceResult(symbol, false, null, null);
    }
}
