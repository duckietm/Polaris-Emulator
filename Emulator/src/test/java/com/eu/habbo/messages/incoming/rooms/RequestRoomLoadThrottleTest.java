package com.eu.habbo.messages.incoming.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RequestRoomLoadThrottleTest {

    private static final long NOW = 1_790_000_000_000L;

    @Test
    void repeatOfTheRoomJustOpenedIsThrottledForOneSecond() {
        assertTrue(RequestRoomLoadEvent.isThrottledRepeat(41, 41, NOW - 500, NOW));
        assertTrue(RequestRoomLoadEvent.isThrottledRepeat(41, 41, NOW - 1000, NOW));
        assertFalse(RequestRoomLoadEvent.isThrottledRepeat(41, 41, NOW - 1001, NOW));
    }

    @Test
    void anotherRoomIsNeverThrottled() {
        // Navigator click, wired forward, room link or teleport right after an entry.
        assertFalse(RequestRoomLoadEvent.isThrottledRepeat(42, 41, NOW - 100, NOW));
        assertFalse(RequestRoomLoadEvent.isThrottledRepeat(42, 41, NOW, NOW));
    }

    @Test
    void firstEntryIsNeverThrottled() {
        assertFalse(RequestRoomLoadEvent.isThrottledRepeat(41, 0, 0, NOW));
    }

    @Test
    void loadingRoomOnlyCountsAsStaleAfterFiveSeconds() {
        assertFalse(RequestRoomLoadEvent.isStaleLoad(NOW - 4000, NOW));
        assertFalse(RequestRoomLoadEvent.isStaleLoad(NOW - 5000, NOW));
        assertTrue(RequestRoomLoadEvent.isStaleLoad(NOW - 5001, NOW));
    }

    @Test
    void handlerThrottlesOnTheOpenedRoomAndLeavesSpawnAndContentsToTheEntry() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/RequestRoomLoadEvent.java"));

        assertTrue(source.contains("isThrottledRepeat(roomId, stats.roomOpenedId, stats.roomOpenedAtMillis, now)"));
        assertTrue(source.contains("info.getLoadingRoom() != 0 && info.getLoadingRoom() != roomId"));
        assertTrue(source.contains(".enterRoomAt(this.client.getHabbo(), roomId, password, spawnX, spawnY)"));
        assertFalse(source.contains("roomEnterTimestamp"));
        assertFalse(source.contains("startBackgroundLoad"));
        assertFalse(source.contains("getLayout()"));
    }

    @Test
    void roomOpenStampsTheThrottleRegardlessOfEntryLogging() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomManager.java"));

        int open = source.indexOf(
                "void openRoom(Habbo habbo, Room room, RoomTile doorLocation, boolean isReconnectSpawn)");
        int mark = source.indexOf("this.markRoomOpened(habbo, room);", open);
        int logs = source.indexOf("hotel.room.enter.logs", open);
        assertTrue(open > 0 && mark > open && mark < logs);
        assertTrue(source.contains("roomOpenedAtMillis = System.currentTimeMillis();"));
    }
}
