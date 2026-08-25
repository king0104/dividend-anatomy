package com.dividendanatomy.ingestion.alphavantage;

import com.dividendanatomy.domain.fundamentals.FinancialFundamentals;
import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.FinancialFundamentalsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Alpha Vantage 4개 엔드포인트(OVERVIEW/BALANCE_SHEET/CASH_FLOW/
 * INCOME_STATEMENT)를 순서대로 호출해 배당 안전도 스코어의 원자재 값을
 * 수집한다. 무료 플랜 초당 1콜 제한(docs/decisions/12)을 지키려고 호출
 * 사이 sleep을 둔다 — 하루 25콜 상한은 코드로 못 피하니 브랜드 풀 전체를
 * 한 번에 돌리지 말 것(IngestionRunner 문서 참고).
 */
@Service
public class AlphaVantageFinancialsIngestionService {

    private static final long RATE_LIMIT_SLEEP_MS = 1200;

    private final AlphaVantageClient client;
    private final FinancialFundamentalsRepository repository;

    public AlphaVantageFinancialsIngestionService(
            AlphaVantageClient client, FinancialFundamentalsRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    @Transactional
    public void ingest(Ticker ticker) {
        String symbol = ticker.getSymbol();

        AlphaVantageOverviewResponse overview = client.fetchOverview(symbol);
        sleep();
        AlphaVantageBalanceSheetResponse balanceSheet = client.fetchBalanceSheet(symbol);
        sleep();
        AlphaVantageCashFlowResponse cashFlow = client.fetchCashFlow(symbol);
        sleep();
        AlphaVantageIncomeStatementResponse incomeStatement = client.fetchIncomeStatement(symbol);

        AlphaVantageBalanceSheetResponse.Report balanceReport = latestReport(balanceSheet.annualReports());
        AlphaVantageCashFlowResponse.Report cashFlowReport = latestReport(cashFlow.annualReports());
        AlphaVantageIncomeStatementResponse.Report incomeReport = latestReport(incomeStatement.annualReports());

        LocalDate fiscalDateEnding = balanceReport != null
                ? LocalDate.parse(balanceReport.fiscalDateEnding())
                : LocalDate.now();

        BigDecimal returnOnEquity = parseDecimalOrNull(overview.returnOnEquityTtm());
        BigDecimal dividendPerShare = parseDecimalOrNull(overview.dividendPerShare());
        BigDecimal eps = parseDecimalOrNull(overview.eps());
        BigDecimal shortLongTermDebtTotal = balanceReport != null ? parseDecimalOrNull(balanceReport.shortLongTermDebtTotal()) : null;
        BigDecimal totalShareholderEquity = balanceReport != null ? parseDecimalOrNull(balanceReport.totalShareholderEquity()) : null;
        BigDecimal operatingCashflow = cashFlowReport != null ? parseDecimalOrNull(cashFlowReport.operatingCashflow()) : null;
        BigDecimal capitalExpenditures = cashFlowReport != null ? parseDecimalOrNull(cashFlowReport.capitalExpenditures()) : null;
        BigDecimal dividendPayout = cashFlowReport != null ? parseDecimalOrNull(cashFlowReport.dividendPayout()) : null;
        BigDecimal ebit = incomeReport != null ? parseDecimalOrNull(incomeReport.ebit()) : null;
        BigDecimal interestExpense = incomeReport != null ? parseDecimalOrNull(incomeReport.interestExpense()) : null;

        repository.findByTicker(ticker).ifPresentOrElse(
                existing -> existing.update(
                        fiscalDateEnding, returnOnEquity, dividendPerShare, eps, dividendPayout,
                        operatingCashflow, capitalExpenditures, shortLongTermDebtTotal,
                        totalShareholderEquity, ebit, interestExpense, DataSource.ALPHA_VANTAGE),
                () -> {
                    FinancialFundamentals fundamentals =
                            new FinancialFundamentals(ticker, fiscalDateEnding, DataSource.ALPHA_VANTAGE);
                    fundamentals.update(
                            fiscalDateEnding, returnOnEquity, dividendPerShare, eps, dividendPayout,
                            operatingCashflow, capitalExpenditures, shortLongTermDebtTotal,
                            totalShareholderEquity, ebit, interestExpense, DataSource.ALPHA_VANTAGE);
                    repository.save(fundamentals);
                });
    }

    private static <T> T latestReport(List<T> reports) {
        return (reports == null || reports.isEmpty()) ? null : reports.get(0);
    }

    /** Alpha Vantage는 결측값을 문자열 "None"으로 준다(docs/decisions/14). */
    private static BigDecimal parseDecimalOrNull(String value) {
        if (value == null || value.equals("None")) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static void sleep() {
        try {
            Thread.sleep(RATE_LIMIT_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
