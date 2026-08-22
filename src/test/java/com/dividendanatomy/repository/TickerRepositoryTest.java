package com.dividendanatomy.repository;

import com.dividendanatomy.domain.market.Ticker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class TickerRepositoryTest {

    @Autowired
    private TickerRepository tickerRepository;

    @Test
    void findsBySymbolAfterSave() {
        tickerRepository.save(new Ticker("KO", "The Coca-Cola Company", "USD"));

        Optional<Ticker> found = tickerRepository.findBySymbol("KO");

        assertTrue(found.isPresent());
        assertEquals("The Coca-Cola Company", found.get().getName());
        assertEquals("USD", found.get().getCurrency());
    }

    @Test
    void returnsEmptyForUnknownSymbol() {
        assertTrue(tickerRepository.findBySymbol("NOPE").isEmpty());
    }
}
