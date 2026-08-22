package com.dividendanatomy.domain.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ticker", uniqueConstraints = @UniqueConstraint(columnNames = "symbol"))
public class Ticker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * 연간 정기 배당 지급 횟수 (분기=4, 월배당=12 ...). 지급 이력에서
     * 자동으로 추론하는 로직은 아직 없어서, 지금은 수동으로 채워 넣는
     * 값으로 취급한다 (docs/specs/yield-change-decomposition.md 0절).
     */
    @Column(name = "regular_payments_per_year")
    private Integer regularPaymentsPerYear;

    protected Ticker() {
    }

    public Ticker(String symbol, String name, String currency) {
        this.symbol = symbol;
        this.name = name;
        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public Integer getRegularPaymentsPerYear() {
        return regularPaymentsPerYear;
    }

    public void setRegularPaymentsPerYear(Integer regularPaymentsPerYear) {
        this.regularPaymentsPerYear = regularPaymentsPerYear;
    }
}
