package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomManagerPersistenceBehaviorTest {

    @Test
    void customLayoutUpsertPreservesEveryCoordinateAndHeightmapParameter() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource)) {
            RoomManager manager = new RoomManager(false);
            Room room = mock(Room.class);
            when(room.getId()).thenReturn(41);

            manager.insertCustomLayout(room, "0\r00", 2, 3, 4);

            RoomJdbcTestSupport.SqlCall insert = dataSource.calls().stream()
                    .filter(call -> call.sql().startsWith("INSERT INTO room_models_custom"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    Map.of(
                            1, 41,
                            2, "custom_41",
                            3, 2,
                            4, 3,
                            5, 4,
                            6, "0\r00",
                            7, 2,
                            8, 3,
                            9, 4,
                            10, "0\r00"),
                    insert.parameters());
        }
    }

    @Test
    void entryLoggingPersistsTheRoomAndUserBeforeRecordingTheVisit() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource)) {
            RoomManager manager = new RoomManager(false);
            Room room = mock(Room.class);
            Habbo habbo = mock(Habbo.class);
            HabboInfo info = mock(HabboInfo.class);
            HabboStats stats = mock(HabboStats.class);
            when(room.getId()).thenReturn(41);
            when(info.getId()).thenReturn(8);
            when(habbo.getHabboInfo()).thenReturn(info);
            when(habbo.getHabboStats()).thenReturn(stats);

            manager.logEnter(habbo, room);

            assertEquals(
                    List.of(Map.of(1, 41, 2, 8)),
                    dataSource.calls().stream()
                            .filter(call -> call.sql().startsWith("INSERT INTO room_enter_log"))
                            .map(call -> Map.of(1, call.parameters().get(1), 2, call.parameters().get(2)))
                            .toList());
            verify(stats).addVisitRoom(41);
        }
    }
}
