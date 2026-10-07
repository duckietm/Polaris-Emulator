package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomFirstVisitTest {

    private static HabboStats statsWithVisits(Set<Integer> visited) {
        HabboStats stats = mock(HabboStats.class);
        when(stats.visitedRoom(anyInt()))
                .thenAnswer(invocation -> visited.contains(invocation.<Integer>getArgument(0)));
        doAnswer(invocation -> visited.add(invocation.getArgument(0)))
                .when(stats)
                .addVisitRoom(anyInt());
        return stats;
    }

    @Test
    void onlyTheFirstEntryOfARoomCountsAndIsRecorded() {
        Set<Integer> visited = new HashSet<>();
        HabboStats stats = statsWithVisits(visited);

        assertTrue(RoomManager.markRoomVisited(stats, 41));
        assertTrue(visited.contains(41), "recorded even when entry logging is off");
        assertFalse(RoomManager.markRoomVisited(stats, 41));
        assertTrue(RoomManager.markRoomVisited(stats, 42));
    }

    @Test
    void theFirstVisitIsDecidedBeforeTheEntryLogMarksTheRoom() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomManager.java"))
                .replaceAll("\\s+", " ");
        int open = source.indexOf(
                "void openRoom(Habbo habbo, Room room, RoomTile doorLocation, boolean isReconnectSpawn)");
        int decided =
                source.indexOf("boolean firstVisit = markRoomVisited(habbo.getHabboStats(), room.getId());", open);
        int logged = source.indexOf("this.logEnter(habbo, room);", open);
        int achievement = source.indexOf("getAchievement(\"RoomEntry\")", open);

        assertTrue(open > 0 && decided > open, "openRoom decides the first visit");
        assertTrue(decided < logged, "before logEnter adds the room to the visited set");
        assertTrue(source.substring(decided, achievement).contains("&& firstVisit)"), "RoomEntry uses that decision");
    }
}
