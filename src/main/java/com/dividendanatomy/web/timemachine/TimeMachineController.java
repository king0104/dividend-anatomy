package com.dividendanatomy.web.timemachine;

import com.dividendanatomy.domain.timemachine.InvestMode;
import com.dividendanatomy.service.timemachine.TimeMachineSimulationResult;
import com.dividendanatomy.service.timemachine.TimeMachineSimulationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/tickers")
public class TimeMachineController {

    private final TimeMachineSimulationService timeMachineSimulationService;

    public TimeMachineController(TimeMachineSimulationService timeMachineSimulationService) {
        this.timeMachineSimulationService = timeMachineSimulationService;
    }

    @GetMapping("/{symbol}/timemachine")
    public TimeMachineSimulationResponse simulate(
            @PathVariable String symbol,
            @RequestParam InvestMode investMode,
            @RequestParam BigDecimal amountKrw,
            @RequestParam int periodYears,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        TimeMachineSimulationResult result =
                timeMachineSimulationService.simulate(symbol, investMode, amountKrw, periodYears, asOf);
        return TimeMachineSimulationResponseMapper.toResponse(result);
    }
}
