package com.eu.habbo.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A dice wrote its result after it had left the room, and a face count of 0 made the roll throw. */
class RandomDiceNumberContractTest {

    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/threading/runnables/RandomDiceNumber.java");

    @Test
    void theDiceMustStillBeInTheRoomAndTheRollIsAtLeastOneFace() throws Exception {
        String source = Files.readString(SOURCE);

        int inRoom = source.indexOf("this.room.getHabboItem(this.item.getId()) != this.item");
        int write = source.indexOf("this.item.setExtradata(");

        assertTrue(inRoom > 0 && inRoom < write);
        assertTrue(source.contains("nextInt(Math.max(1, this.maxNumber))"));
    }
}
