package com.dividendanatomy.web.growth;

import com.dividendanatomy.service.growth.DividendGrowthDecelerationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/tickers")
public class GrowthDecelerationController {

    private final DividendGrowthDecelerationService dividendGrowthDecelerationService;

    public GrowthDecelerationController(DividendGrowthDecelerationService dividendGrowthDecelerationService) {
        this.dividendGrowthDecelerationService = dividendGrowthDecelerationService;
    }

    @GetMapping("/{symbol}/growth-deceleration")
    public GrowthDecelerationResponse getGrowthDeceleration(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return GrowthDecelerationResponseMapper.toResponse(
                symbol, asOf, dividendGrowthDecelerationService.evaluate(symbol, asOf));
    }
}
