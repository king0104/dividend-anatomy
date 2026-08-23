package com.dividendanatomy.web.fx;

import com.dividendanatomy.service.fx.KrwDividendConversionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickers")
public class KrwDividendConversionController {

    private final KrwDividendConversionService krwDividendConversionService;

    public KrwDividendConversionController(KrwDividendConversionService krwDividendConversionService) {
        this.krwDividendConversionService = krwDividendConversionService;
    }

    @GetMapping("/{symbol}/krw-dividends")
    public KrwDividendConversionResponse getKrwDividends(@PathVariable String symbol) {
        return KrwDividendConversionResponseMapper.toResponse(
                symbol, krwDividendConversionService.getKrwConvertedDividends(symbol));
    }
}
