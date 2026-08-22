package com.dividendanatomy.web.yield;

import com.dividendanatomy.service.yield.YieldDecompositionResult;
import com.dividendanatomy.service.yield.YieldDecompositionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/tickers")
public class YieldDecompositionController {

    private final YieldDecompositionService yieldDecompositionService;

    public YieldDecompositionController(YieldDecompositionService yieldDecompositionService) {
        this.yieldDecompositionService = yieldDecompositionService;
    }

    @GetMapping("/{symbol}/yield-decomposition")
    public YieldDecompositionResponse getYieldDecomposition(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        YieldDecompositionResult result = yieldDecompositionService.decompose(symbol, asOf);
        return YieldDecompositionResponseMapper.toResponse(result);
    }
}
