package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomPersistenceBehaviorTest {

    @Test
    void guildGetterReturnsLoadedStateWithoutQueryingPersistence()
            throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource =
                new RoomJdbcTestSupport.RecordingDataSource();
        dataSource.rows(sql -> List.of(Map.of("guild_id", 23)));
        Room room = new Room(
                41,
                7,
                new RoomDependencies(dataSource::getConnection));

        assertEquals(0, room.getGuildId());
        assertEquals(0, room.getGuildId());
        assertEquals(0, dataSource.connectionCount());
        assertEquals(List.of(), dataSource.calls());
    }

    @Test
    void wiredReadsUseTheEstablishedQueryAndCacheResults()
            throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource =
                new RoomJdbcTestSupport.RecordingDataSource();
        dataSource.rows(sql -> {
            if (sql.startsWith(
                    "SELECT inspect_mask, modify_mask FROM room_wired_settings")) {
                return List.of(Map.of(
                        "inspect_mask", 5,
                        "modify_mask", 4));
            }
            return List.of();
        });

        Room room = new Room(
                41,
                7,
                new RoomDependencies(dataSource::getConnection));

        assertEquals(13, room.getWiredInspectMask());
        assertEquals(12, room.getWiredModifyMask());
        assertEquals(13, room.getWiredInspectMask());
        assertEquals(12, room.getWiredModifyMask());

        assertEquals(1, dataSource.calls().size());
        assertEquals(
                "SELECT inspect_mask, modify_mask FROM room_wired_settings "
                        + "WHERE room_id = ? LIMIT 1",
                dataSource.calls().get(0).sql());
        assertEquals(Map.of(1, 41), dataSource.calls().get(0).parameters());
    }

    @Test
    void userCountUpdateRetainsItsExactWriteContract() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource =
                new RoomJdbcTestSupport.RecordingDataSource();

        Room room = new Room(
                41,
                7,
                new RoomDependencies(dataSource::getConnection));

        room.updateDatabaseUserCount();

        assertEquals(1, dataSource.calls().size());
        RoomJdbcTestSupport.SqlCall call = dataSource.calls().getFirst();
        assertEquals(
                "UPDATE rooms SET users = ? WHERE id = ? LIMIT 1",
                call.sql());
        assertEquals(Map.of(1, 0, 2, 41), call.parameters());
        assertEquals("update", call.operation());
    }

    @Test
    void roomJoinSchedulesOneUserCountWriteOutsideTheRoomUnitLock()
            throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource =
                new RoomJdbcTestSupport.RecordingDataSource();
        List<Runnable> queued = new ArrayList<>();
        AtomicBoolean scheduledUnderRoomUnitLock = new AtomicBoolean();
        Room[] holder = new Room[1];
        Room room = new Room(
                41,
                7,
                new RoomDependencies(
                        dataSource::getConnection,
                        task -> {
                            scheduledUnderRoomUnitLock.set(
                                    Thread.holdsLock(holder[0].roomUnitLock));
                            queued.add(task);
                        }));
        holder[0] = room;
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        RoomUnit roomUnit = mock(RoomUnit.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(9);
        when(habbo.getRoomUnit()).thenReturn(roomUnit);

        room.getUnitManager().addHabbo(habbo);

        assertFalse(scheduledUnderRoomUnitLock.get());
        assertEquals(1, queued.size());
        assertEquals(List.of(), dataSource.calls());

        queued.removeFirst().run();
        assertEquals(1, dataSource.calls().size());
        assertEquals(
                Map.of(1, 1, 2, 41),
                dataSource.calls().getFirst().parameters());
    }
}
