package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomPersistenceBehaviorTest {

    @Test
    void hiddenGuildAndWiredReadsUseTheEstablishedQueriesAndCacheResults()
            throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource =
                new RoomJdbcTestSupport.RecordingDataSource();
        dataSource.rows(sql -> {
            if (sql.startsWith("SELECT guild_id FROM rooms")) {
                return List.of(Map.of("guild_id", 23));
            }
            if (sql.startsWith(
                    "SELECT inspect_mask, modify_mask FROM room_wired_settings")) {
                return List.of(Map.of(
                        "inspect_mask", 5,
                        "modify_mask", 4));
            }
            return List.of();
        });

        try (RoomJdbcTestSupport.InstalledDatabase ignored =
                     RoomJdbcTestSupport.install(dataSource)) {
            Room room = new Room(41, 7);

            assertEquals(23, room.getGuildId());
            assertEquals(23, room.getGuildId());
            assertEquals(13, room.getWiredInspectMask());
            assertEquals(12, room.getWiredModifyMask());
            assertEquals(13, room.getWiredInspectMask());
            assertEquals(12, room.getWiredModifyMask());

            assertEquals(2, dataSource.calls().size());
            assertEquals(
                    "SELECT guild_id FROM rooms WHERE id = ? LIMIT 1",
                    dataSource.calls().get(0).sql());
            assertEquals(Map.of(1, 41), dataSource.calls().get(0).parameters());
            assertEquals(
                    "SELECT inspect_mask, modify_mask FROM room_wired_settings "
                            + "WHERE room_id = ? LIMIT 1",
                    dataSource.calls().get(1).sql());
            assertEquals(Map.of(1, 41), dataSource.calls().get(1).parameters());
        }
    }

    @Test
    void userCountUpdateRetainsItsExactWriteContract() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource =
                new RoomJdbcTestSupport.RecordingDataSource();

        try (RoomJdbcTestSupport.InstalledDatabase ignored =
                     RoomJdbcTestSupport.install(dataSource)) {
            Room room = new Room(41, 7);

            room.updateDatabaseUserCount();

            assertEquals(1, dataSource.calls().size());
            RoomJdbcTestSupport.SqlCall call = dataSource.calls().getFirst();
            assertEquals(
                    "UPDATE rooms SET users = ? WHERE id = ? LIMIT 1",
                    call.sql());
            assertEquals(Map.of(1, 0, 2, 41), call.parameters());
            assertEquals("update", call.operation());
        }
    }
}
