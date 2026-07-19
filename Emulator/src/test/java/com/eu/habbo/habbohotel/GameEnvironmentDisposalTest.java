package com.eu.habbo.habbohotel;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEnvironmentDisposalTest {

    @Test
    void partialEnvironmentCanBeDisposedSafely() {
        assertDoesNotThrow(() -> new GameEnvironment().dispose());
    }

    @Test
    void failedAndMissingStepsDoNotPreventLaterDisposal() {
        AtomicBoolean laterStepRan = new AtomicBoolean();
        Map<String, Runnable> steps = new LinkedHashMap<>();
        steps.put("missing", null);
        steps.put("failing", () -> {
            throw new IllegalStateException("expected");
        });
        steps.put("later", () -> laterStepRan.set(true));

        GameEnvironment.disposeAll(steps);

        assertTrue(laterStepRan.get());
    }

    @Test
    void roomsAreQuiescedBeforeTheRoomSavePassAndBotsAreDisposed() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/GameEnvironment.java"
        ));
        int quiesce = source.indexOf("this.roomManager.quiesceRoomCycles()");
        int rooms = source.indexOf("this.roomManager.dispose()");
        int bots = source.indexOf("this.botManager.dispose()");

        assertTrue(quiesce >= 0);
        assertTrue(rooms > quiesce);
        assertTrue(bots >= 0);
    }
}
