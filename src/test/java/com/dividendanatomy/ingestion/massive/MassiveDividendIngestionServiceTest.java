package com.dividendanatomy.ingestion.massive;

import com.dividendanatomy.domain.dividend.DividendPayment;
import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.market.Ticker;
import com.dividendanatomy.repository.DividendPaymentRepository;
import com.dividendanatomy.repository.TickerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class MassiveDividendIngestionServiceTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Autowired
    private DividendPaymentRepository dividendPaymentRepository;

    private static MassiveClient stubClient(List<MassiveDividend> fixedDividends) {
        return new MassiveClient(RestClient.builder(), "http://unused", "unused") {
            @Override
            public List<MassiveDividend> fetchAllDividends(String ticker) {
                return fixedDividends;
            }
        };
    }

    @Test
    void savesDividendsClassifiesSpecialAndSetsRegularPaymentsPerYear() {
        Ticker cost = tickerRepository.save(new Ticker("COST", "Costco Wholesale Corporation", "USD"));

        MassiveDividendIngestionService service = new MassiveDividendIngestionService(
                stubClient(List.of(
                        new MassiveDividend(new BigDecimal("1.02"), "CD", "2023-11-02", "2023-11-02", "2023-11-17", 4, "COST"),
                        new MassiveDividend(new BigDecimal("15"), "SC", "2023-12-27", "2023-12-27", "2024-01-12", 0, "COST"),
                        new MassiveDividend(new BigDecimal("1.16"), "CD", "2024-02-01", "2024-02-01", "2024-02-22", 4, "COST")
                )),
                dividendPaymentRepository, tickerRepository);

        int savedCount = service.ingest(cost);

        assertEquals(3, savedCount);
        assertEquals(4, cost.getRegularPaymentsPerYear());

        Optional<DividendPayment> special = dividendPaymentRepository
                .findByTickerAndExDividendDate(cost, LocalDate.parse("2023-12-27"));
        assertTrue(special.isPresent());
        assertEquals(DividendType.SPECIAL, special.get().getType());
    }

    @Test
    void skipsAlreadyIngestedPayments() {
        Ticker cost = tickerRepository.save(new Ticker("COST", "Costco Wholesale Corporation", "USD"));
        MassiveDividendIngestionService service = new MassiveDividendIngestionService(
                stubClient(List.of(
                        new MassiveDividend(new BigDecimal("1.02"), "CD", "2023-11-02", "2023-11-02", "2023-11-17", 4, "COST")
                )),
                dividendPaymentRepository, tickerRepository);

        int firstRun = service.ingest(cost);
        int secondRun = service.ingest(cost);

        assertEquals(1, firstRun);
        assertEquals(0, secondRun);
        assertEquals(1, dividendPaymentRepository.findAll().size());
    }
}
