package com.dividendanatomy.web.dividenddisclosure;

import com.dividendanatomy.service.dividenddisclosure.SpecialDividendDisclosureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickers")
public class SpecialDividendDisclosureController {

    private final SpecialDividendDisclosureService specialDividendDisclosureService;

    public SpecialDividendDisclosureController(SpecialDividendDisclosureService specialDividendDisclosureService) {
        this.specialDividendDisclosureService = specialDividendDisclosureService;
    }

    @GetMapping("/{symbol}/special-dividends")
    public SpecialDividendDisclosureResponse getSpecialDividends(@PathVariable String symbol) {
        return SpecialDividendDisclosureResponseMapper.toResponse(
                symbol, specialDividendDisclosureService.getDisclosure(symbol));
    }
}
