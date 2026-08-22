package com.dividendanatomy.web.dividendcut;

import com.dividendanatomy.service.dividendcut.DividendCutDetectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickers")
public class DividendCutController {

    private final DividendCutDetectionService dividendCutDetectionService;

    public DividendCutController(DividendCutDetectionService dividendCutDetectionService) {
        this.dividendCutDetectionService = dividendCutDetectionService;
    }

    @GetMapping("/{symbol}/dividend-cuts")
    public DividendCutResponse getDividendCuts(@PathVariable String symbol) {
        return DividendCutResponseMapper.toResponse(symbol, dividendCutDetectionService.detectCuts(symbol));
    }
}
