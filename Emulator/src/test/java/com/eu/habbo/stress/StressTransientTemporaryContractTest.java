package com.eu.habbo.stress;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Stress-test furni belong to the room owner, who could pick them up into the inventory and trade or recycle them. */
class StressTransientTemporaryContractTest {

    @Test
    void stressFurniAreTemporary() throws Exception {
        Path stress = Path.of("src/main/java/com/eu/habbo/stress");

        assertTrue(Files.readString(stress.resolve("StressTransientItem.java")).contains("this.markTemporary();"));
        assertTrue(Files.readString(stress.resolve("StressTransientRoller.java")).contains("this.markTemporary();"));
    }
}
