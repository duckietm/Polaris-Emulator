package com.eu.habbo.threading.runnables;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The single-callback constructor put the failure callback with the success ones. */
class RoomUnitWalkToLocationCallbacksTest {

    @Test
    void theFailureCallbackGoesWithTheFailures() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/threading/runnables/RoomUnitWalkToLocation.java"));

        assertTrue(source.contains("if (failedReached != null) this.failedReached.add(failedReached);"));
        assertTrue(!source.contains("if (failedReached != null) this.targetReached.add(failedReached);"));
    }
}
