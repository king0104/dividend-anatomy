package com.dividendanatomy.web.fx;

import com.dividendanatomy.domain.fx.FxConversionStatus;
import com.dividendanatomy.domain.fx.KrwConvertedEntry;
import com.dividendanatomy.service.fx.KrwDividendConversionService;
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

@WebMvcTest(KrwDividendConversionController.class)
class KrwDividendConversionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KrwDividendConversionService krwDividendConversionService;

    @Test
    void returnsKrwDividendsOnSuccess() throws Exception {
        KrwConvertedEntry entry = new KrwConvertedEntry(
                LocalDate.parse("2026-06-15"), new BigDecimal("1.00"), new BigDecimal("0.85"),
                FxConversionStatus.CONVERTED, new BigDecimal("1400"), new BigDecimal("1400"), new BigDecimal("1190"));
        when(krwDividendConversionService.getKrwConvertedDividends(anyString())).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/tickers/KO/krw-dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickerSymbol").value("KO"))
                .andExpect(jsonPath("$.entries[0].status").value("CONVERTED"))
                .andExpect(jsonPath("$.entries[0].netAmountKrw").value(1190));
    }

    @Test
    void returns404WhenTickerUnknown() throws Exception {
        when(krwDividendConversionService.getKrwConvertedDividends(anyString()))
                .thenThrow(new NoSuchElementException("알 수 없는 티커: NOPE"));

        mockMvc.perform(get("/api/tickers/NOPE/krw-dividends"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns422WhenCurrencyIsNotUsd() throws Exception {
        when(krwDividendConversionService.getKrwConvertedDividends(anyString()))
                .thenThrow(new IllegalStateException("이 지표는 미국 원천징수(USD)만 계산한다"));

        mockMvc.perform(get("/api/tickers/KRSTOCK/krw-dividends"))
                .andExpect(status().isUnprocessableContent());
    }
}
