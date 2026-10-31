package com.eu.habbo.messages.incoming.navigator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A room could be created for zero or negative visitors, and with a negative trade mode. */
class RequestCreateRoomLimitsContractTest {

    @Test
    void theVisitorCountAndTheTradeModeAreCheckedOnBothSides() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/navigator/RequestCreateRoomEvent.java"));

        assertTrue(source.contains("if (maxUsers < 1 || maxUsers > 250) return;"));
        assertTrue(source.contains("if (tradeType < 0 || tradeType > 2) return;"));
    }
}
