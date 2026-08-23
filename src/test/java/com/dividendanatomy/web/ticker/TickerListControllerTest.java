package com.dividendanatomy.web.ticker;

import com.dividendanatomy.domain.ticker.CurrentYieldResult;
import com.dividendanatomy.domain.ticker.DividendIncreaseStreakResult;
import com.dividendanatomy.domain.ticker.DividendIncreaseStreakStatus;
import com.dividendanatomy.domain.ticker.TickerSummary;
import com.dividendanatomy.service.ticker.TickerSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TickerListController.class)
class TickerListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TickerSummaryService tickerSummaryService;

    @Test
    void returnsTickerListOnSuccess() throws Exception {
        TickerSummary summary = new TickerSummary(
                "KO", "Coca-Cola", "USD", Optional.of(new BigDecimal("65.00")), 4,
                new CurrentYieldResult(Optional.of(new BigDecimal("0.0435")), true),
                new DividendIncreaseStreakResult(DividendIncreaseStreakStatus.CALCULATED, 62));
        when(tickerSummaryService.summarizeAll()).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/tickers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickers[0].symbol").value("KO"))
                .andExpect(jsonPath("$.tickers[0].streakYears").value(62));
    }

    @Test
    void returnsEmptyListWhenNoTickers() throws Exception {
        when(tickerSummaryService.summarizeAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/tickers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickers").isEmpty());
    }
}
