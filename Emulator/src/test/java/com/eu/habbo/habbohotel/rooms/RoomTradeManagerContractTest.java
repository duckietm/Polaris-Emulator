package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RoomTradeManagerContractTest {
    private static String roomTradeManagerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomTradeManager.java"));
    }

    @Test
    void startTradeRejectsParticipantsAlreadyInActiveTradeInsideLock() throws Exception {
        String source = roomTradeManagerSource();
        int synchronizedBlock = source.indexOf("synchronized (this.activeTrades)");
        int activeGuard = source.indexOf("hasActiveTrade(userOne) || this.hasActiveTrade(userTwo)");
        int addTrade = source.indexOf("this.activeTrades.add(trade)");

        assertTrue(synchronizedBlock > -1, "RoomTradeManager.startTrade must lock activeTrades before mutation");
        assertTrue(activeGuard > synchronizedBlock,
                "startTrade must check both participants for an existing active trade while holding the activeTrades lock");
        assertTrue(activeGuard < addTrade,
                "duplicate participant guard must run before a new RoomTrade is added");
        assertTrue(source.contains("private boolean hasActiveTrade(Habbo user)"),
                "active trade lookup should be reusable under the same activeTrades lock");
    }
}
