package com.dividendanatomy.web.ticker;

import java.util.List;

public record TickerListResponse(List<TickerSummaryResponse> tickers) {
}
