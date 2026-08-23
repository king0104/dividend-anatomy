package com.dividendanatomy.web.dividenddisclosure;

import com.dividendanatomy.domain.dividend.DividendType;
import com.dividendanatomy.domain.dividenddisclosure.DisclosureEntry;
import com.dividendanatomy.domain.dividenddisclosure.SpecialDividendDisclosure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialDividendDisclosureResponseMapperTest {

    @Test
    void mapsFieldsThroughWithoutRounding() {
        DisclosureEntry regular = new DisclosureEntry(
                LocalDate.parse("2023-08-24"), new BigDecimal("1.02"), DividendType.REGULAR, false, null);
        DisclosureEntry special = new DisclosureEntry(
                LocalDate.parse("2023-12-27"), new BigDecimal("15"), DividendType.SPECIAL, true, "특별배당 사유");
        SpecialDividendDisclosure disclosure = new SpecialDividendDisclosure(List.of(regular, special), 1, 1);

        SpecialDividendDisclosureResponse response = SpecialDividendDisclosureResponseMapper.toResponse("COST", disclosure);

        assertEquals("COST", response.tickerSymbol());
        assertEquals(1, response.regularCount());
        assertEquals(1, response.specialCount());
        assertEquals(2, response.entries().size());

        DisclosureEntryResponse regularResponse = response.entries().get(0);
        assertFalse(regularResponse.excluded());
        assertNull(regularResponse.exclusionReason());
        assertEquals(0, new BigDecimal("1.02").compareTo(regularResponse.amount()));

        DisclosureEntryResponse specialResponse = response.entries().get(1);
        assertTrue(specialResponse.excluded());
        assertEquals("특별배당 사유", specialResponse.exclusionReason());
        assertEquals(0, new BigDecimal("15").compareTo(specialResponse.amount()));
    }

    @Test
    void mapsEmptyDisclosure() {
        SpecialDividendDisclosure disclosure = new SpecialDividendDisclosure(List.of(), 0, 0);

        SpecialDividendDisclosureResponse response = SpecialDividendDisclosureResponseMapper.toResponse("EMPTY", disclosure);

        assertEquals(0, response.regularCount());
        assertEquals(0, response.specialCount());
        assertTrue(response.entries().isEmpty());
    }
}
