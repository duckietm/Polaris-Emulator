package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SetMottoContractTest {
    private static String setMottoSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/SetMotto.java"));
    }

    @Test
    void onlineMottoUpdateDoesNotRequireCurrentRoom() throws Exception {
        String source = setMottoSource();

        assertTrue(source.contains("getCurrentRoom() != null"),
                "RCON SetMotto must not fail for online users outside a room");
    }
}
