package com.dividendanatomy.web.dividendcut;

import com.dividendanatomy.domain.dividendcut.CutComparisonResult;
import com.dividendanatomy.domain.dividendcut.CutComparisonStatus;
import com.dividendanatomy.service.dividendcut.DividendCutDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DividendCutController.class)
class DividendCutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DividendCutDetectionService dividendCutDetectionService;

    @Test
    void returnsComparisonsOnSuccess() throws Exception {
        CutComparisonResult cut = new CutComparisonResult(
                LocalDate.parse("2025-04-01"), CutComparisonStatus.CUT,
                new BigDecimal("4.00"), new BigDecimal("3.60"), new BigDecimal("10.00"));
        when(dividendCutDetectionService.detectCuts(anyString())).thenReturn(List.of(cut));

        mockMvc.perform(get("/api/tickers/KO/dividend-cuts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickerSymbol").value("KO"))
                .andExpect(jsonPath("$.comparisons[0].status").value("CUT"))
                .andExpect(jsonPath("$.comparisons[0].decreasePercent").value(10.00));
    }

    @Test
    void returns404WhenTickerUnknown() throws Exception {
        when(dividendCutDetectionService.detectCuts(anyString()))
                .thenThrow(new NoSuchElementException("알 수 없는 티커: NOPE"));

        mockMvc.perform(get("/api/tickers/NOPE/dividend-cuts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("알 수 없는 티커: NOPE"));
    }

    @Test
    void returns422WhenRegularPaymentsPerYearMissing() throws Exception {
        when(dividendCutDetectionService.detectCuts(anyString()))
                .thenThrow(new IllegalStateException("NOFREQ: 연간 정기 배당 지급 횟수가 설정되지 않음 (regularPaymentsPerYear)"));

        mockMvc.perform(get("/api/tickers/NOFREQ/dividend-cuts"))
                .andExpect(status().isUnprocessableContent());
    }
}
