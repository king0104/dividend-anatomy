package com.dividendanatomy.domain.market;

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
 * 종가 한 건. close는 Twelve Data 기준 이미 split-adjusted다
 * (docs/decisions/03-split-adjustment.md — NVDA 2024-06-10 10:1 분할
 * 전후로 가격이 연속적임을 확인). 배당(raw)과 단위가 다르다는 점을
 * 반드시 염두에 두고 TTM 집계 시 조정해야 한다.
 */
@Entity
@Table(name = "price_bar", uniqueConstraints = @UniqueConstraint(columnNames = {"ticker_id", "date"}))
public class PriceBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal close;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSource source;

    protected PriceBar() {
    }

    public PriceBar(Ticker ticker, LocalDate date, BigDecimal close, DataSource source) {
        this.ticker = ticker;
        this.date = date;
        this.close = close;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getClose() {
        return close;
    }

    public DataSource getSource() {
        return source;
    }

    /**
     * Twelve Data가 분할 발생 후 과거 종가를 재조정(back-adjust)해서 다시
     * 내려줄 수 있으므로, 같은 (ticker, date)를 재수집했을 때 raw 배당처럼
     * 원본을 보존할 이유가 없다 — 최신 조정값으로 갱신한다.
     */
    public void updateClose(BigDecimal close, DataSource source) {
        this.close = close;
        this.source = source;
    }
}
