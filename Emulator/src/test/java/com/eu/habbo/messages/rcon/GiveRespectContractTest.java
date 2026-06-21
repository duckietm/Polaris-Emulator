package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GiveRespectContractTest {
    private static String giveRespectSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/GiveRespect.java"));
    }

    @Test
    void offlineRespectGrantBindsGivenAndReceivedToMatchingColumns() throws Exception {
        String source = giveRespectSource();

        assertTrue(source.contains("statement.setInt(1, object.respect_given);"),
                "respects_given must be incremented with respect_given");
        assertTrue(source.contains("statement.setInt(2, object.respect_received);"),
                "respects_received must be incremented with respect_received");
        assertTrue(source.contains("RconGrantGuard.validateNonNegativeAmount"),
                "RCON respect grants must reject negative values");
        assertTrue(source.contains("statement.executeUpdate() == 0"),
                "Offline RCON respect grants must report missing users when the UPDATE changes no rows");
    }
}
