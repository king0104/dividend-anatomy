package com.dividendanatomy.domain.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 통화쌍 하나의 일별 환율. 과거 환율은 재조정되지 않으므로(PriceBar의
 * 분할 재조정과 달리) 갱신 메서드가 없다 — 한번 저장되면 불변.
 */
@Entity
@Table(name = "exchange_rate", uniqueConstraints = @UniqueConstraint(columnNames = {"from_currency", "to_currency", "date"}))
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSource source;

    protected ExchangeRate() {
    }

    public ExchangeRate(String fromCurrency, String toCurrency, LocalDate date, BigDecimal rate, DataSource source) {
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.date = date;
        this.rate = rate;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public String getFromCurrency() {
        return fromCurrency;
    }

    public String getToCurrency() {
        return toCurrency;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public DataSource getSource() {
        return source;
    }
}
