package com.dividendanatomy.web.volatility;

import com.dividendanatomy.domain.volatility.VolatilityResult;
import com.dividendanatomy.domain.volatility.VolatilityStatus;
import com.dividendanatomy.service.volatility.DividendVolatilityService;
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

@WebMvcTest(VolatilityController.class)
class VolatilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DividendVolatilityService dividendVolatilityService;

    @Test
    void returnsVolatilityOnSuccess() throws Exception {
        VolatilityResult result = new VolatilityResult(
                VolatilityStatus.COMPLETE,
                Optional.of(new BigDecimal("0.0435")),
                Optional.of(new BigDecimal("0.0119")));
        when(dividendVolatilityService.evaluate(anyString(), any(LocalDate.class))).thenReturn(result);

        mockMvc.perform(get("/api/tickers/KO/volatility").param("asOf", "2026-08-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickerSymbol").value("KO"))
                .andExpect(jsonPath("$.status").value("COMPLETE"))
                .andExpect(jsonPath("$.meanGrowthRatePercent").value(4.35))
                .andExpect(jsonPath("$.standardDeviationPercent").value(1.19));
    }

    @Test
    void returns404WhenTickerUnknown() throws Exception {
        when(dividendVolatilityService.evaluate(anyString(), any(LocalDate.class)))
                .thenThrow(new NoSuchElementException("알 수 없는 티커: NOPE"));

        mockMvc.perform(get("/api/tickers/NOPE/volatility").param("asOf", "2026-08-22"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("알 수 없는 티커: NOPE"));
    }

    @Test
    void returns422WhenRegularPaymentsPerYearMissing() throws Exception {
        when(dividendVolatilityService.evaluate(anyString(), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("NOFREQ: 연간 정기 배당 지급 횟수가 설정되지 않음 (regularPaymentsPerYear)"));

        mockMvc.perform(get("/api/tickers/NOFREQ/volatility").param("asOf", "2026-08-22"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void returns400WhenAsOfParamMissing() throws Exception {
        mockMvc.perform(get("/api/tickers/KO/volatility"))
                .andExpect(status().isBadRequest());
    }
}
