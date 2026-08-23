package com.dividendanatomy.web.growth;

import com.dividendanatomy.domain.growth.GrowthDecelerationResult;
import com.dividendanatomy.domain.growth.GrowthDecelerationStatus;
import com.dividendanatomy.service.growth.DividendGrowthDecelerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GrowthDecelerationController.class)
class GrowthDecelerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DividendGrowthDecelerationService dividendGrowthDecelerationService;

    @Test
    void returnsDecelerationOnSuccess() throws Exception {
        GrowthDecelerationResult result = new GrowthDecelerationResult(
                Optional.of(new BigDecimal("0.05")),
                Optional.of(new BigDecimal("0.10")),
                GrowthDecelerationStatus.DECELERATING,
                Optional.of(new BigDecimal("0.05")));
        when(dividendGrowthDecelerationService.evaluate(anyString(), any(LocalDate.class))).thenReturn(result);

        mockMvc.perform(get("/api/tickers/KO/growth-deceleration").param("asOf", "2026-08-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickerSymbol").value("KO"))
                .andExpect(jsonPath("$.status").value("DECELERATING"))
                .andExpect(jsonPath("$.cagrShortPercent").value(5.00))
                .andExpect(jsonPath("$.cagrLongPercent").value(10.00))
                .andExpect(jsonPath("$.decelerationGapPercent").value(5.00));
    }

    @Test
    void returns404WhenTickerUnknown() throws Exception {
        when(dividendGrowthDecelerationService.evaluate(anyString(), any(LocalDate.class)))
                .thenThrow(new NoSuchElementException("알 수 없는 티커: NOPE"));

        mockMvc.perform(get("/api/tickers/NOPE/growth-deceleration").param("asOf", "2026-08-22"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("알 수 없는 티커: NOPE"));
    }

    @Test
    void returns422WhenRegularPaymentsPerYearMissing() throws Exception {
        when(dividendGrowthDecelerationService.evaluate(anyString(), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("NOFREQ: 연간 정기 배당 지급 횟수가 설정되지 않음 (regularPaymentsPerYear)"));

        mockMvc.perform(get("/api/tickers/NOFREQ/growth-deceleration").param("asOf", "2026-08-22"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void returns400WhenAsOfParamMissing() throws Exception {
        mockMvc.perform(get("/api/tickers/KO/growth-deceleration"))
                .andExpect(status().isBadRequest());
    }
}
