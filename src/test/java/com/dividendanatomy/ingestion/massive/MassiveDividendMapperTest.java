package com.dividendanatomy.ingestion.massive;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.Ticker;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 이번 세션에서 실제로 받은 COST 배당 이력(알려진 2023-12-27 $15
 * 특별배당 포함)을 그대로 테스트 리소스로 써서 REGULAR/SPECIAL 분류를
 * 검증한다 (docs/decisions/04-dividend-classification.md).
 */
class MassiveDividendMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void classifiesRealCapturedCostDividendsCorrectly() throws Exception {
        MassiveDividendsResponse response;
        try (InputStream in = getClass().getResourceAsStream("/massive/cost-dividends.json")) {
            response = MAPPER.readValue(in, MassiveDividendsResponse.class);
        }

        Ticker cost = new Ticker("COST", "Costco Wholesale Corporation", "USD");
        List<DividendPayment> payments = response.results().stream()
                .map(d -> MassiveDividendMapper.toDividendPayment(d, cost))
                .toList();

        assertEquals(7, payments.size());

        DividendPayment special = payments.stream()
                .filter(p -> p.getExDividendDate().equals(LocalDate.parse("2023-12-27")))
                .findFirst().orElseThrow();
        assertEquals(DividendType.SPECIAL, special.getType());
        assertEquals(0, new BigDecimal("15").compareTo(special.getAmount()));

        long regularCount = payments.stream().filter(p -> p.getType() == DividendType.REGULAR).count();
        assertEquals(6, regularCount);
    }

    @Test
    void throwsOnUnknownDividendType() {
        MassiveDividend unknown = new MassiveDividend(
                new BigDecimal("1.00"), "LT", "2026-01-01", "2026-01-01", "2026-01-15", 4, "XYZ");
        Ticker xyz = new Ticker("XYZ", "Unknown Co", "USD");

        assertThrows(IllegalArgumentException.class, () -> MassiveDividendMapper.toDividendPayment(unknown, xyz));
    }
}
