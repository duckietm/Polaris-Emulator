package com.eu.habbo.messages.incoming.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Taking a saddle off cleared it from the horse before the saddle item existed. */
class HorseRemoveSaddleOrderContractTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/pets/HorseRemoveSaddleEvent.java");

    @Test
    void theItemIsCreatedBeforeTheSaddleLeavesTheHorse() throws Exception {
        String source = Files.readString(SOURCE);
        int create = source.indexOf("createItem(");
        int nullCheck = source.indexOf("if (saddle == null) return;");
        int unsaddle = source.indexOf("horse.hasSaddle(false);");

        assertTrue(create > 0 && nullCheck > create && unsaddle > nullCheck);
        assertTrue(source.contains("if (room == null) return;"));
    }
}
