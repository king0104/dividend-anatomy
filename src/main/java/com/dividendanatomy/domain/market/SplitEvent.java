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
 * 주식 분할 이력. ratio = split_to / split_from (Massive
 * /v3/reference/splits 필드 그대로 대응) — 예: 2:1 분할이면 2,
 * 1:2 역분할이면 0.5. raw 배당 금액(DividendPayment.amount)을 현재
 * 주식 수 기준으로 환산하려면 그 배당 이후 발생한 모든 분할의 ratio를
 * 누적해서 나눠야 한다 (docs/decisions/03-split-adjustment.md).
 */
@Entity
@Table(name = "split_event", uniqueConstraints = @UniqueConstraint(columnNames = {"ticker_id", "execution_date"}))
public class SplitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(name = "execution_date", nullable = false)
    private LocalDate executionDate;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal ratio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSource source;

    protected SplitEvent() {
    }

    public SplitEvent(Ticker ticker, LocalDate executionDate, BigDecimal ratio, DataSource source) {
        this.ticker = ticker;
        this.executionDate = executionDate;
        this.ratio = ratio;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public LocalDate getExecutionDate() {
        return executionDate;
    }

    public BigDecimal getRatio() {
        return ratio;
    }

    public DataSource getSource() {
        return source;
    }
}
