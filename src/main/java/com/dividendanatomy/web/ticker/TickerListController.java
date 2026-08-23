package com.dividendanatomy.web.ticker;

import com.dividendanatomy.service.ticker.TickerSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TickerListController {

    private final TickerSummaryService tickerSummaryService;

    public TickerListController(TickerSummaryService tickerSummaryService) {
        this.tickerSummaryService = tickerSummaryService;
    }

    @GetMapping("/api/tickers")
    public TickerListResponse getTickers() {
        return TickerListResponseMapper.toResponse(tickerSummaryService.summarizeAll());
    }
}
