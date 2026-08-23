package com.dividendanatomy.web.dividenddisclosure;

import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.dividenddisclosure.DisclosureEntry;
import com.dividendanatomy.domain.dividenddisclosure.SpecialDividendDisclosure;
import com.dividendanatomy.service.dividenddisclosure.SpecialDividendDisclosureService;
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

@WebMvcTest(SpecialDividendDisclosureController.class)
class SpecialDividendDisclosureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpecialDividendDisclosureService specialDividendDisclosureService;

    @Test
    void returnsDisclosureOnSuccess() throws Exception {
        DisclosureEntry special = new DisclosureEntry(
                LocalDate.parse("2023-12-27"), new BigDecimal("15"), DividendType.SPECIAL, true, "특별배당 사유");
        SpecialDividendDisclosure disclosure = new SpecialDividendDisclosure(List.of(special), 0, 1);
        when(specialDividendDisclosureService.getDisclosure(anyString())).thenReturn(disclosure);

        mockMvc.perform(get("/api/tickers/COST/special-dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickerSymbol").value("COST"))
                .andExpect(jsonPath("$.regularCount").value(0))
                .andExpect(jsonPath("$.specialCount").value(1))
                .andExpect(jsonPath("$.entries[0].excluded").value(true));
    }

    @Test
    void returns404WhenTickerUnknown() throws Exception {
        when(specialDividendDisclosureService.getDisclosure(anyString()))
                .thenThrow(new NoSuchElementException("알 수 없는 티커: NOPE"));

        mockMvc.perform(get("/api/tickers/NOPE/special-dividends"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("알 수 없는 티커: NOPE"));
    }
}
