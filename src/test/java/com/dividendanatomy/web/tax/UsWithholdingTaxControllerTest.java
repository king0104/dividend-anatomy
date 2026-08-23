package com.dividendanatomy.web.tax;

import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.tax.NetDividendEntry;
import com.dividendanatomy.domain.tax.NetDividendSummary;
import com.dividendanatomy.service.tax.UsWithholdingTaxService;
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

@WebMvcTest(UsWithholdingTaxController.class)
class UsWithholdingTaxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsWithholdingTaxService usWithholdingTaxService;

    @Test
    void returnsNetDividendsOnSuccess() throws Exception {
        NetDividendEntry entry = new NetDividendEntry(
                LocalDate.parse("2026-06-15"), new BigDecimal("1.00"), new BigDecimal("0.85"), DividendType.REGULAR);
        when(usWithholdingTaxService.getNetDividends(anyString())).thenReturn(new NetDividendSummary(List.of(entry)));

        mockMvc.perform(get("/api/tickers/KO/net-dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickerSymbol").value("KO"))
                .andExpect(jsonPath("$.entries[0].netAmount").value(0.85));
    }

    @Test
    void returns404WhenTickerUnknown() throws Exception {
        when(usWithholdingTaxService.getNetDividends(anyString()))
                .thenThrow(new NoSuchElementException("알 수 없는 티커: NOPE"));

        mockMvc.perform(get("/api/tickers/NOPE/net-dividends"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns422WhenCurrencyIsNotUsd() throws Exception {
        when(usWithholdingTaxService.getNetDividends(anyString()))
                .thenThrow(new IllegalStateException("KRSTOCK: 이 지표는 미국 원천징수(USD)만 계산하며 통화가 USD가 아닌 종목은 지원하지 않는다"));

        mockMvc.perform(get("/api/tickers/KRSTOCK/net-dividends"))
                .andExpect(status().isUnprocessableContent());
    }
}
