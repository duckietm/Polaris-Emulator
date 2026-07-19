package com.eu.habbo.habbohotel.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void internalPollingReportsEmptyStockWithoutThrowing() {
        assertTrue(configurationWith().pollNumber().isEmpty());
    }

    @Test
    void concurrentPollingReservesTheLastNumberOnce() throws Exception {
        CatalogLimitedConfiguration configuration = configurationWith(17);
        int buyers = 8;
        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(buyers);

        try {
            List<Future<OptionalInt>> reservations = new ArrayList<>();
            for (int buyer = 0; buyer < buyers; buyer++) {
                reservations.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return configuration.pollNumber();
                }));
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            int reserved = 0;
            for (Future<OptionalInt> reservation : reservations) {
                if (reservation.get(2, TimeUnit.SECONDS).isPresent()) reserved++;
            }

            assertEquals(1, reserved);
            assertEquals(0, configuration.available());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void purchasePathMapsAnEmptyReservationToSoldOut() throws Exception {
        String manager = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java"
        ));
        String configuration = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/catalog/CatalogLimitedConfiguration.java"
        ));

        assertTrue(manager.contains("limitedConfiguration.pollNumber()"));
        assertTrue(manager.contains("limitedNumberReservation.isEmpty()"));
        assertFalse(manager.contains("limitedNumber = limitedConfiguration.getNumber()"));
        assertTrue(configuration.contains("this.limitedNumbers.pollFirst()"));
        assertTrue(configuration.contains(
                "public int available() {\n        synchronized (this.limitedNumbers) {"
        ));
    }

    private static CatalogLimitedConfiguration configurationWith(Integer... numbers) {
        return new CatalogLimitedConfiguration(
                1,
                new LinkedList<>(List.of(numbers)),
                numbers.length,
                false
        );
    }
}
