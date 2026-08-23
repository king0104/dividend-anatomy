package com.dividendanatomy.web.tax;

import com.dividendanatomy.service.tax.UsWithholdingTaxService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickers")
public class UsWithholdingTaxController {

    private final UsWithholdingTaxService usWithholdingTaxService;

    public UsWithholdingTaxController(UsWithholdingTaxService usWithholdingTaxService) {
        this.usWithholdingTaxService = usWithholdingTaxService;
    }

    @GetMapping("/{symbol}/net-dividends")
    public NetDividendSummaryResponse getNetDividends(@PathVariable String symbol) {
        return UsWithholdingTaxResponseMapper.toResponse(symbol, usWithholdingTaxService.getNetDividends(symbol));
    }
}
