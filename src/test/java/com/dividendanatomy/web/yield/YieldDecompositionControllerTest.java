package com.dividendanatomy.web.yield;

import com.dividendanatomy.domain.market.DataSource;
import com.dividendanatomy.domain.market.PriceBar;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.domain.yield.TtmDividendSummary;
import com.dividendanatomy.domain.yield.YieldContribution;
import com.dividendanatomy.service.yield.YieldDecompositionResult;
import com.dividendanatomy.service.yield.YieldDecompositionService;
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

@WebMvcTest(YieldDecompositionController.class)
class YieldDecompositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private YieldDecompositionService yieldDecompositionService;

    private YieldDecompositionResult sampleResult() {
        Ticker ticker = new Ticker("KO", "The Coca-Cola Company", "USD");
        LocalDate t1 = LocalDate.parse("2026-08-22");
        LocalDate t0 = t1.minusYears(1);
        PriceBar priceAtT0 = new PriceBar(ticker, t0, new BigDecimal("100.00"), DataSource.TWELVE_DATA);
        PriceBar priceAtT1 = new PriceBar(ticker, t1, new BigDecimal("80.00"), DataSource.TWELVE_DATA);
        TtmDividendSummary ttm0 = new TtmDividendSummary(new BigDecimal("3.00"), new BigDecimal("3.00"), 4, 4);
        TtmDividendSummary ttm1 = new TtmDividendSummary(new BigDecimal("3.60"), new BigDecimal("3.60"), 4, 4);
        YieldContribution actual = new YieldContribution(new BigDecimal("0.00825"), new BigDecimal("0.00675"));
        return new YieldDecompositionResult("KO", t0, t1, priceAtT0, priceAtT1, ttm0, ttm1, actual, Optional.of(actual));
    }

    @Test
    void returnsRoundedContributionsOnSuccess() throws Exception {
        when(yieldDecompositionService.decompose(anyString(), any(LocalDate.class))).thenReturn(sampleResult());

        mockMvc.perform(get("/api/tickers/KO/yield-decomposition").param("asOf", "2026-08-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickerSymbol").value("KO"))
                .andExpect(jsonPath("$.actual.priceContributionPercent").value(0.83))
                .andExpect(jsonPath("$.actual.dividendContributionPercent").value(0.68))
                .andExpect(jsonPath("$.annualized").exists())
                .andExpect(jsonPath("$.dataQuality.ttmCompleteAtT0").value(true));
    }

    @Test
    void returns404WhenTickerOrPriceMissing() throws Exception {
        when(yieldDecompositionService.decompose(anyString(), any(LocalDate.class)))
                .thenThrow(new NoSuchElementException("알 수 없는 티커: NOPE"));

        mockMvc.perform(get("/api/tickers/NOPE/yield-decomposition").param("asOf", "2026-08-22"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("알 수 없는 티커: NOPE"));
    }

    @Test
    void returns422WhenRegularPaymentsPerYearMissing() throws Exception {
        when(yieldDecompositionService.decompose(anyString(), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("NOFREQ: 연간 정기 배당 지급 횟수가 설정되지 않음 (regularPaymentsPerYear)"));

        mockMvc.perform(get("/api/tickers/NOFREQ/yield-decomposition").param("asOf", "2026-08-22"))
                .andExpect(status().isUnprocessableContent()); // Spring 7.0부터 isUnprocessableEntity() 대체 (상태 코드는 그대로 422)
    }

    @Test
    void returns400WhenAsOfParamMissing() throws Exception {
        mockMvc.perform(get("/api/tickers/KO/yield-decomposition"))
                .andExpect(status().isBadRequest());
    }
}
