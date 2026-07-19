package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomLoadPublicationContractTest {

    @Test
    void roomLoadDoesNotHoldAnUnusedOuterConnection() throws Exception {
        String loadMethod = loadDataInternalSource();

        assertFalse(loadMethod.contains(
                "try (Connection connection = Emulator.getDatabase().getDataSource().getConnection())"),
                "loadDataInternal must not reserve a connection that its child loads never use");
    }

    @Test
    void promotionLoadPublishesOnceAndCompletesWithTheRoomLoad() throws Exception {
        String loadMethod = loadDataInternalSource();

        assertFalse(loadMethod.contains("this.promoted = false;"),
                "promotion state must not publish a transient false value");
        assertTrue(loadMethod.contains("promotionFuture"),
                "promotion loading must have an explicit completion future");
        assertTrue(loadMethod.contains("CompletableFuture.allOf(promotionFuture,"),
                "room loading must wait for promotion state before publishing loaded");
    }

    private static String loadDataInternalSource() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/Room.java"));
        int start = source.indexOf("private void loadDataInternal()");
        int end = source.indexOf("private synchronized void loadLayout()", start);
        return source.substring(start, end);
    }
}
