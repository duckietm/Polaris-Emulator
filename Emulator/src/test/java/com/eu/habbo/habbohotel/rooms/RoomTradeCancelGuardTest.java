package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomTradeCancelGuardTest {
    @Test
    void cancellingSharesTheLockAndTheCompletionGuardWithConfirm() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomTrade.java"));
        int stop = source.indexOf("public synchronized void stopTrade(Habbo habbo)");
        int returnItems = source.indexOf("this.returnItems()", stop);
        int guard = source.indexOf("if (this.completed) return;", stop);

        assertTrue(stop > -1, "stopTrade must hold the same lock as confirm()");
        assertTrue(
                guard > stop && guard < returnItems,
                "a trade that already completed must not return its items to their old owners");
    }
}
