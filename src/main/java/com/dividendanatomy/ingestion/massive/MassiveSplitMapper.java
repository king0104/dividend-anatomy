package com.dividendanatomy.ingestion.massive;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.SplitEvent;
import com.dividendanatomy.domain.market.Ticker;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/** 순수 변환 — HTTP 없음, DB 없음. ratio = split_to / split_from. */
public final class MassiveSplitMapper {

    private static final MathContext MC = MathContext.DECIMAL64;

    private MassiveSplitMapper() {
    }

    public static SplitEvent toSplitEvent(MassiveSplit split, Ticker ticker) {
        BigDecimal ratio = BigDecimal.valueOf(split.splitTo())
                .divide(BigDecimal.valueOf(split.splitFrom()), MC);
        return new SplitEvent(ticker, LocalDate.parse(split.executionDate()), ratio, DataSource.MASSIVE);
    }
}
