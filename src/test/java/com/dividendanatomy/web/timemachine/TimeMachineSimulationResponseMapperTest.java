package com.dividendanatomy.web.timemachine;

import com.dividendanatomy.domain.timemachine.TimeMachineResult;
import com.dividendanatomy.service.timemachine.TimeMachineSimulationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeMachineSimulationResponseMapperTest {

    @Test
    void roundsKrwToIntegerAndReturnRatioToTwoDecimalPercent() {
        LocalDate checkpoint = LocalDate.parse("2026-08-25");
        TimeMachineResult usdResult = new TimeMachineResult(
                new BigDecimal("104.04"),
                new BigDecimal("104"),
                new BigDecimal("0.04"),
                new BigDecimal("0.0404"),
                List.of(new TimeMachineResult.YearlySnapshot(checkpoint, new BigDecimal("104.04"), new BigDecimal("104"))));

        TimeMachineSimulationResult result = new TimeMachineSimulationResult(
                "TMTEST", 2, 2, true, usdResult,
                new BigDecimal("1000.4567"),
                new BigDecimal("104088.5"),
                new BigDecimal("104047.6"),
                new BigDecimal("40.9"));

        TimeMachineSimulationResponse response = TimeMachineSimulationResponseMapper.toResponse(result);

        assertEquals(new BigDecimal("104089"), response.finalValueReinvestKrw());
        assertEquals(new BigDecimal("104048"), response.finalValueNoReinvestKrw());
        assertEquals(new BigDecimal("41"), response.differenceKrw());
        assertEquals(new BigDecimal("4.04"), response.totalReturnPercent());
        assertEquals(1, response.yearlySeries().size());
        assertEquals(checkpoint, response.yearlySeries().get(0).checkpointDate());
        // 손계산: 104.04 * 1000.4567 = 104087.515068 -> HALF_UP 반올림 -> 104088
        assertEquals(new BigDecimal("104088"), response.yearlySeries().get(0).reinvestValueKrw());
    }
}
