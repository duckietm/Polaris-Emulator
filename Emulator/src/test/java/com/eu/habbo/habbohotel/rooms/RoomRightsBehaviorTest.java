package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import it.unimi.dsi.fastutil.ints.IntList;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomRightsBehaviorTest {

    @Test
    void roomRightsLoadPreservesOrderDuplicatesAndThePublicLiveList() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        dataSource.rows(sql -> sql.contains("FROM room_rights")
                ? List.of(row("user_id", 12), row("user_id", 20), row("user_id", 12))
                : List.of());
        Room room = new Room(41, 7);
        IntList publicRights = room.getRights();

        try (Connection connection = dataSource.getConnection()) {
            room.getRightsManager().loadRights(connection);
        }

        assertSame(publicRights, room.getRights());
        assertEquals(IntList.of(12, 20, 12), publicRights);
        publicRights.add(99);
        assertEquals(IntList.of(12, 20, 12, 99), room.getRights());

        RoomJdbcTestSupport.SqlCall call = dataSource.calls().getFirst();
        assertEquals("SELECT user_id FROM room_rights WHERE room_id = ?", call.sql());
        assertEquals(41, call.parameters().get(1));
    }

    @Test
    void databaseLoadedRightsAreImmediatelyRecognisedByHasRights() throws Exception {
        // Characterises the facade split: rights read from the database now populate the same
        // backing store that Room.hasRights() consults, so a rights holder is recognised as soon
        // as the room finishes loading — without waiting for a runtime giveRights() re-sync.
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        dataSource.rows(sql -> sql.contains("FROM room_rights") ? List.of(row("user_id", 12)) : List.of());
        Room room = new Room(41, 7);

        try (Connection connection = dataSource.getConnection()) {
            room.getRightsManager().loadRights(connection);
        }

        Habbo rightsHolder = mock(Habbo.class);
        HabboInfo rightsHolderInfo = mock(HabboInfo.class);
        when(rightsHolder.getHabboInfo()).thenReturn(rightsHolderInfo);
        when(rightsHolderInfo.getId()).thenReturn(12);

        Habbo stranger = mock(Habbo.class);
        HabboInfo strangerInfo = mock(HabboInfo.class);
        when(stranger.getHabboInfo()).thenReturn(strangerInfo);
        when(strangerInfo.getId()).thenReturn(30);

        assertTrue(room.hasRights(rightsHolder), "Database-loaded rights holder must have rights after load");
        assertTrue(room.getRights().contains(12));
        assertFalse(room.hasRights(stranger));
    }

    @Test
    void rightsManagerLoadsMembershipAndResolvesThePublicUserMap() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        dataSource.rows(sql -> {
            if (sql.contains("INNER JOIN users")) {
                return List.of(row("user_id", 12, "username", "alice"), row("user_id", 20, "username", "bob"));
            }
            if (sql.contains("FROM room_rights")) {
                return List.of(row("user_id", 12), row("user_id", 20), row("user_id", 12));
            }
            return List.of();
        });

        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource);
                Connection connection = dataSource.getConnection()) {
            Room room = new Room(41, 7);

            room.getRightsManager().loadRights(connection);

            assertEquals(Map.of(12, "alice", 20, "bob"), room.getRightsManager().getUsersWithRights());
        }

        assertEquals(2, dataSource.calls().size());
        assertEquals(41, dataSource.calls().get(0).parameters().get(1));
        assertEquals(41, dataSource.calls().get(1).parameters().get(1));
    }

    private static Map<String, Object> row(String column, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(column, value);
        return row;
    }

    private static Map<String, Object> row(
            String firstColumn, Object firstValue, String secondColumn, Object secondValue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(firstColumn, firstValue);
        row.put(secondColumn, secondValue);
        return row;
    }
}
