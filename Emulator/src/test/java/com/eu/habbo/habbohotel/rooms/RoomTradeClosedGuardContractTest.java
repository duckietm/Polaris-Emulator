package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Offers and accepts reached a trade that was already completed or cancelled. */
class RoomTradeClosedGuardContractTest {

    @Test
    void aClosedTradeTakesNoOfferRemovalOrAccept() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomTrade.java"));

        for (String method : new String[] {
            "public synchronized void offerItem(",
            "public synchronized void offerMultipleItems(",
            "public synchronized void removeItem(",
            "public synchronized void accept("
        }) {
            String body = source.substring(source.indexOf(method));
            int guard = body.indexOf("if (this.completed) return;");
            assertTrue(guard > 0 && guard < body.indexOf("getRoomTradeUserForHabbo(habbo)"), method);
        }
    }
}
