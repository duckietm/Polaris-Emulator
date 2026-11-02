package com.eu.habbo.messages.incoming.navigator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The new navigator leaves invisible rooms out; the legacy search ("owner:name", a room name) listed them to
 * anyone, with name and description, and the answer was cached for the whole rank.
 */
class SearchRoomsInvisibleTest {

    private static Room room(RoomState state) {
        Room room = mock(Room.class);
        when(room.getState()).thenReturn(state);
        return room;
    }

    @Test
    void invisibleRoomsAreLeftOut() {
        Room open = room(RoomState.OPEN);
        Room locked = room(RoomState.LOCKED);
        Room invisible = room(RoomState.INVISIBLE);

        assertEquals(List.of(open, locked), SearchRoomsEvent.visibleRooms(List.of(open, invisible, locked)));
    }

    @Test
    void staffWhoMayEnterAnyRoomStillSeeThemInTheirOwnCacheEntry() throws Exception {
        String source = Files.readString(
                        Path.of("src/main/java/com/eu/habbo/messages/incoming/navigator/SearchRoomsEvent.java"))
                .replaceAll("\\s+", " ");

        assertTrue(source.contains("hasPermission(Permission.ACC_ENTERANYROOM)"));
        assertTrue(source.contains("if (!showInvisible) rooms = visibleRooms(rooms);"));
        assertTrue(source.contains("(showInvisible ? \"\\tall\" : \"\")"), "the two answers are cached apart");
    }
}
