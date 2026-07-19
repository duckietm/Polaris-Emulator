package com.eu.habbo.habbohotel.catalog;

import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogLimitedConfigurationTest {

    @Test
    void publicNumberDrawingKeepsItsExistingLifoAndEmptyBehavior() {
        CatalogLimitedConfiguration configuration = configurationWith(1, 2);

        assertEquals(2, configuration.getNumber());
        assertEquals(1, configuration.getNumber());
        assertThrows(NoSuchElementException.class, configuration::getNumber);
    }

    @Test
    void internalPollingReturnsAnAvailableNumber() {
        CatalogLimitedConfiguration configuration = configurationWith(17);

        OptionalInt number = configuration.pollNumber();

        assertTrue(number.isPresent());
        assertEquals(17, number.getAsInt());
        assertEquals(0, configuration.available());
    }

    private static CatalogLimitedConfiguration configurationWith(Integer... numbers) {
        return new CatalogLimitedConfiguration(
                1,
                new LinkedList<>(java.util.List.of(numbers)),
                numbers.length,
                false
        );
    }
}
