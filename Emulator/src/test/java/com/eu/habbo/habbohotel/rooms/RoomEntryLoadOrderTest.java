package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Room entry loads only rights for the access checks and the full room contents once entry is
 * granted, so a refused entry never loads items, bots, pets, heightmap or wired.
 */
class RoomEntryLoadOrderTest {

    private final List<String> events = new ArrayList<>();

    private RoomEntryService service(Room room, boolean pluginAllows) {
        return new RoomEntryService(
                roomId -> {
                    this.events.add("lookup");
                    return room;
                },
                ignored -> this.events.add("access"),
                ignored -> this.events.add("content"),
                ignored -> room,
                (habbo, ignored) -> {
                    this.events.add("plugin");
                    return pluginAllows;
                },
                (habbo, opened, door, reconnect) -> this.events.add("open"));
    }

    @Test
    void openRoomLoadsAccessDataThenContentsThenOpens() {
        Room room = room(RoomState.OPEN);

        this.service(room, true).enter(habbo(), 41, "", false, null, false);

        assertEquals(List.of("lookup", "access", "plugin", "content", "open"), this.events);
    }

    @Test
    void correctPasswordLoadsContentsRightBeforeOpening() {
        Room room = room(RoomState.PASSWORD);
        when(room.getPassword()).thenReturn("secret");

        this.service(room, true).enter(habbo(), 41, "SECRET", false, null, false);

        assertEquals(List.of("lookup", "access", "plugin", "content", "open"), this.events);
    }

    @Test
    void wrongPasswordNeverLoadsContents() {
        Room room = room(RoomState.PASSWORD);
        when(room.getPassword()).thenReturn("secret");

        this.service(room, true).enter(habbo(), 41, "wrong", false, null, false);

        assertEquals(List.of("lookup", "access", "plugin"), this.events);
    }

    @Test
    void bannedUserNeverLoadsContents() {
        Room room = room(RoomState.OPEN);
        Habbo habbo = habbo();
        when(room.isBanned(habbo)).thenReturn(true);

        this.service(room, true).enter(habbo, 41, "", false, null, false);

        assertEquals(List.of("lookup", "access", "plugin"), this.events);
    }

    @Test
    void cancelledEntryNeverLoadsContents() {
        Room room = room(RoomState.OPEN);

        this.service(room, false).enter(habbo(), 41, "", false, null, false);

        assertEquals(List.of("lookup", "access", "plugin"), this.events);
    }

    @Test
    void refusedDoorbellNeverLoadsContents() {
        Room room = mock(Room.class, withSettings().useConstructor(41, 7));
        when(room.getId()).thenReturn(41);
        when(room.getState()).thenReturn(RoomState.LOCKED);
        when(room.getHabbos()).thenReturn(List.of());

        this.service(room, true).enter(habbo(), 41, "", false, null, false);

        assertEquals(List.of("lookup", "access", "plugin"), this.events);
    }

    @Test
    void rightsLoadedForTheAccessCheckOpenALockedRoom() {
        Room room = room(RoomState.LOCKED);
        Habbo habbo = habbo();
        when(room.hasRights(habbo)).thenReturn(true);

        this.service(room, true).enter(habbo, 41, "", false, null, false);

        assertEquals(List.of("lookup", "access", "plugin", "content", "open"), this.events);
    }

    @Test
    void approvedDoorbellStillLoadsTheFullRoom() {
        Room room = room(RoomState.LOCKED);

        this.service(room, true).enter(habbo(), 41, "", true, null, false);

        assertEquals(List.of("lookup", "access", "plugin", "content", "open"), this.events);
    }

    @Test
    void reconnectSpawnIsResolvedAfterTheColdRoomContentsAreLoaded() {
        Room room = room(RoomState.OPEN);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile tile = mock(RoomTile.class);
        when(tile.isWalkable()).thenReturn(true);
        when(layout.getTile((short) 5, (short) 6)).thenReturn(tile);
        // A cold room has no layout until its contents are loaded.
        when(room.getLayout()).thenAnswer(ignored -> this.events.contains("content") ? layout : null);
        SpawnRecorder opened = new SpawnRecorder();

        this.service(room, opened).enter(habbo(), 41, "", false, RoomEntryService.Spawn.reconnectAt(5, 6));

        assertSame(tile, opened.door);
        assertTrue(opened.reconnect);
        assertEquals(List.of("lookup", "access", "plugin", "content", "open"), this.events);
    }

    @Test
    void reconnectSpawnOnAnUnwalkableOrMissingTileFallsBackToTheDoor() {
        Room room = room(RoomState.OPEN);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile blocked = mock(RoomTile.class);
        when(layout.getTile((short) 5, (short) 6)).thenReturn(blocked);
        when(room.getLayout()).thenReturn(layout);

        SpawnRecorder unwalkable = new SpawnRecorder();
        this.service(room, unwalkable).enter(habbo(), 41, "", false, RoomEntryService.Spawn.reconnectAt(5, 6));
        assertNull(unwalkable.door);
        assertFalse(unwalkable.reconnect);

        SpawnRecorder missing = new SpawnRecorder();
        this.service(room, missing).enter(habbo(), 41, "", false, RoomEntryService.Spawn.reconnectAt(30, 30));
        assertNull(missing.door);
        assertFalse(missing.reconnect);

        SpawnRecorder door = new SpawnRecorder();
        this.service(room, door).enter(habbo(), 41, "", false, RoomEntryService.Spawn.reconnectAt(-1, -1));
        assertNull(door.door);
        assertFalse(door.reconnect);
    }

    @Test
    void fixedSpawnTilesPassThroughUnchanged() {
        Room room = room(RoomState.OPEN);
        RoomTile teleport = mock(RoomTile.class);
        SpawnRecorder opened = new SpawnRecorder();

        this.service(room, opened).enter(habbo(), 41, "", false, teleport, false);

        assertSame(teleport, opened.door);
        assertFalse(opened.reconnect);
    }

    private RoomEntryService service(Room room, SpawnRecorder opened) {
        return new RoomEntryService(
                roomId -> {
                    this.events.add("lookup");
                    return room;
                },
                ignored -> this.events.add("access"),
                ignored -> this.events.add("content"),
                ignored -> room,
                (habbo, ignored) -> {
                    this.events.add("plugin");
                    return true;
                },
                (habbo, target, door, reconnect) -> {
                    this.events.add("open");
                    opened.door = door;
                    opened.reconnect = reconnect;
                });
    }

    private static final class SpawnRecorder {
        private RoomTile door;
        private boolean reconnect;
    }

    @Test
    void contentLoadOnlyRunsForRoomsThatAreNotLoadedYet() {
        RoomManager manager = new RoomManager(false);

        Room cold = mock(Room.class);
        when(cold.isPreLoaded()).thenReturn(true);
        manager.ensureRoomDataLoaded(cold);
        verify(cold).loadData();

        Room loaded = mock(Room.class);
        when(loaded.isLoaded()).thenReturn(true);
        manager.ensureRoomDataLoaded(loaded);
        verify(loaded, never()).loadData();
        verify(loaded, never()).waitForLoad();

        Room loading = mock(Room.class);
        when(loading.isLoadingInProgress()).thenReturn(true);
        manager.ensureRoomDataLoaded(loading);
        verify(loading).waitForLoad();
        verify(loading, never()).loadData();
    }

    @Test
    void accessDataLoadReadsOnlyRightsAndLeavesTheRoomUnloaded() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        dataSource.rows(sql -> sql.contains("FROM room_rights") ? List.of(row(12)) : List.of());
        Room room = new Room(41, 7, new RoomDependencies(dataSource::getConnection));
        Habbo rightsHolder = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(rightsHolder.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(12);

        room.loadAccessData();

        assertTrue(room.hasRights(rightsHolder));
        assertFalse(room.isLoaded());
        assertTrue(room.isPreLoaded());
        assertEquals(
                List.of("SELECT user_id FROM room_rights WHERE room_id = ?"),
                dataSource.calls().stream()
                        .map(RoomJdbcTestSupport.SqlCall::sql)
                        .toList());
    }

    @Test
    void repeatedRightsLoadsReplaceInsteadOfDuplicating() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        dataSource.rows(sql -> sql.contains("FROM room_rights") ? List.of(row(12)) : List.of());
        Room room = new Room(41, 7, new RoomDependencies(dataSource::getConnection));

        room.loadAccessData();
        try (Connection connection = dataSource.getConnection()) {
            room.getRightsManager().loadRights(connection);
        }

        assertEquals(List.of(12), List.copyOf(room.getRights()));
    }

    private static Map<String, Object> row(int userId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", userId);
        return row;
    }

    private static Room room(RoomState state) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(41);
        when(room.getState()).thenReturn(state);
        return room;
    }

    private static Habbo habbo() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        GameClient client = mock(GameClient.class);
        when(info.getRoomQueueId()).thenReturn(41);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getClient()).thenReturn(client);
        return habbo;
    }
}
