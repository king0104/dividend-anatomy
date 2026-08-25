package com.dividendanatomy.web.safety;

import com.dividendanatomy.service.safety.DividendSafetyScoreService;
import com.dividendanatomy.service.safety.DividendSafetyScoreServiceResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickers")
public class DividendSafetyScoreController {

    private final DividendSafetyScoreService dividendSafetyScoreService;

    public DividendSafetyScoreController(DividendSafetyScoreService dividendSafetyScoreService) {
        this.dividendSafetyScoreService = dividendSafetyScoreService;
    }

    @GetMapping("/{symbol}/safety-score")
    public DividendSafetyScoreResponse getSafetyScore(@PathVariable String symbol) {
        DividendSafetyScoreServiceResult result = dividendSafetyScoreService.getScore(symbol);
        return DividendSafetyScoreResponseMapper.toResponse(result);
    }
}
