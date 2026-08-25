package com.dividendanatomy.domain.fundamentals;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.Ticker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 배당 안전도 스코어 계산에 필요한 재무제표 원자재 값. 티커당 1행만 유지하는
 * 최신 스냅샷(시계열 아님) — Alpha Vantage OVERVIEW/BALANCE_SHEET/CASH_FLOW/
 * INCOME_STATEMENT의 최신 연간 보고서 기준(docs/decisions/12, 14). 일부
 * 종목은 특정 필드가 결측일 수 있어 전부 nullable이다 — 서비스 계층이
 * 5개 다 있는지 확인 후 점수를 계산한다(도메인 계산기는 null을 안 받음).
 */
@Entity
@Table(name = "financial_fundamentals", uniqueConstraints = @UniqueConstraint(columnNames = "ticker_id"))
public class FinancialFundamentals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(nullable = false)
    private LocalDate fiscalDateEnding;

    @Column(precision = 19, scale = 6)
    private BigDecimal returnOnEquity;

    @Column(precision = 19, scale = 6)
    private BigDecimal dividendPerShare;

    @Column(precision = 19, scale = 6)
    private BigDecimal eps;

    @Column(precision = 19, scale = 6)
    private BigDecimal dividendPayout;

    @Column(precision = 19, scale = 6)
    private BigDecimal operatingCashflow;

    @Column(precision = 19, scale = 6)
    private BigDecimal capitalExpenditures;

    @Column(precision = 19, scale = 6)
    private BigDecimal shortLongTermDebtTotal;

    @Column(precision = 19, scale = 6)
    private BigDecimal totalShareholderEquity;

    @Column(precision = 19, scale = 6)
    private BigDecimal ebit;

    @Column(precision = 19, scale = 6)
    private BigDecimal interestExpense;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSource source;

    protected FinancialFundamentals() {
    }

    public FinancialFundamentals(Ticker ticker, LocalDate fiscalDateEnding, DataSource source) {
        this.ticker = ticker;
        this.fiscalDateEnding = fiscalDateEnding;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public LocalDate getFiscalDateEnding() {
        return fiscalDateEnding;
    }

    public BigDecimal getReturnOnEquity() {
        return returnOnEquity;
    }

    public BigDecimal getDividendPerShare() {
        return dividendPerShare;
    }

    public BigDecimal getEps() {
        return eps;
    }

    public BigDecimal getDividendPayout() {
        return dividendPayout;
    }

    public BigDecimal getOperatingCashflow() {
        return operatingCashflow;
    }

    public BigDecimal getCapitalExpenditures() {
        return capitalExpenditures;
    }

    public BigDecimal getShortLongTermDebtTotal() {
        return shortLongTermDebtTotal;
    }

    public BigDecimal getTotalShareholderEquity() {
        return totalShareholderEquity;
    }

    public BigDecimal getEbit() {
        return ebit;
    }

    public BigDecimal getInterestExpense() {
        return interestExpense;
    }

    public DataSource getSource() {
        return source;
    }

    /** 재수집 시 최신 스냅샷으로 전체 교체(시계열이 아니라 "현재 상태"만 유지). */
    public void update(
            LocalDate fiscalDateEnding,
            BigDecimal returnOnEquity,
            BigDecimal dividendPerShare,
            BigDecimal eps,
            BigDecimal dividendPayout,
            BigDecimal operatingCashflow,
            BigDecimal capitalExpenditures,
            BigDecimal shortLongTermDebtTotal,
            BigDecimal totalShareholderEquity,
            BigDecimal ebit,
            BigDecimal interestExpense,
            DataSource source) {
        this.fiscalDateEnding = fiscalDateEnding;
        this.returnOnEquity = returnOnEquity;
        this.dividendPerShare = dividendPerShare;
        this.eps = eps;
        this.dividendPayout = dividendPayout;
        this.operatingCashflow = operatingCashflow;
        this.capitalExpenditures = capitalExpenditures;
        this.shortLongTermDebtTotal = shortLongTermDebtTotal;
        this.totalShareholderEquity = totalShareholderEquity;
        this.ebit = ebit;
        this.interestExpense = interestExpense;
        this.source = source;
    }
}
