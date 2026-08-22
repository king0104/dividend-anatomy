package com.dividendanatomy.domain.dividend;

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
 * 배당 지급 한 건. amount는 Massive 원본 그대로(raw, 분할 미조정) —
 * 절대 여기서 분할 조정을 하지 않는다 (docs/decisions/03-split-adjustment.md).
 * 조정은 TTM 집계 시점에 SplitEvent를 조회해서 계산한다.
 */
@Entity
@Table(name = "dividend_payment", uniqueConstraints = @UniqueConstraint(columnNames = {"ticker_id", "ex_dividend_date"}))
public class DividendPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(name = "ex_dividend_date", nullable = false)
    private LocalDate exDividendDate;

    /** T+1 전환(2024-05-29) 이후엔 보통 exDividendDate와 동일 (docs/decisions/02-ex-dividend-t1-rule.md). */
    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "pay_date")
    private LocalDate payDate;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DividendType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSource source;

    protected DividendPayment() {
    }

    public DividendPayment(Ticker ticker, LocalDate exDividendDate, LocalDate recordDate, LocalDate payDate,
            BigDecimal amount, DividendType type, DataSource source) {
        this.ticker = ticker;
        this.exDividendDate = exDividendDate;
        this.recordDate = recordDate;
        this.payDate = payDate;
        this.amount = amount;
        this.type = type;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public LocalDate getExDividendDate() {
        return exDividendDate;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public DividendType getType() {
        return type;
    }

    public DataSource getSource() {
        return source;
    }
}
