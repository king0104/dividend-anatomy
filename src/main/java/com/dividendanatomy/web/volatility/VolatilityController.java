package com.dividendanatomy.web.volatility;

import com.dividendanatomy.service.volatility.DividendVolatilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/tickers")
public class VolatilityController {

    private final DividendVolatilityService dividendVolatilityService;

    public VolatilityController(DividendVolatilityService dividendVolatilityService) {
        this.dividendVolatilityService = dividendVolatilityService;
    }

    @GetMapping("/{symbol}/volatility")
    public VolatilityResponse getVolatility(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return VolatilityResponseMapper.toResponse(symbol, asOf, dividendVolatilityService.evaluate(symbol, asOf));
    }
}
