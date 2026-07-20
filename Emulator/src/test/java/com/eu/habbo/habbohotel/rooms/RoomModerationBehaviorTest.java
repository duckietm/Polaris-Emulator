package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomModerationBehaviorTest {

    private GameEnvironment originalEnvironment;
    private GameEnvironment environment;
    private HabboManager habbos;

    @BeforeEach
    void installEnvironment() throws Exception {
        Field field = Emulator.class.getDeclaredField("gameEnvironment");
        field.setAccessible(true);
        this.originalEnvironment = (GameEnvironment) field.get(null);
        this.environment = mock(GameEnvironment.class);
        this.habbos = mock(HabboManager.class);
        when(this.environment.getHabboManager()).thenReturn(this.habbos);
        field.set(null, this.environment);
    }

    @AfterEach
    void restoreEnvironment() throws Exception {
        Field field = Emulator.class.getDeclaredField("gameEnvironment");
        field.setAccessible(true);
        field.set(null, this.originalEnvironment);
    }

    @Test
    void rejectsMissingRightsAndOwnerTargetsBeforeUserLookup() {
        RoomManager manager = new RoomManager(false);
        Room room = activeRoom(manager, 41, 7);
        Habbo moderator = mock(Habbo.class);
        when(room.hasRights(moderator)).thenReturn(false);

        manager.banUserFromRoom(moderator, 8, 41, RoomManager.RoomBanTypes.RWUAM_BAN_USER_HOUR);
        manager.banUserFromRoom(null, 7, 41, RoomManager.RoomBanTypes.RWUAM_BAN_USER_HOUR);

        verify(this.environment, never()).getHabboManager();
        verify(room, never()).addRoomBan(any());
    }

    @Test
    void rejectsOnlineUnkickableUsersWithoutPersistingABan() {
        RoomManager manager = new RoomManager(false);
        Room room = activeRoom(manager, 41, 7);
        Habbo target = onlineTarget(8, "protected");
        when(target.hasPermission(Permission.ACC_UNKICKABLE)).thenReturn(true);

        manager.banUserFromRoom(null, 8, 41, RoomManager.RoomBanTypes.RWUAM_BAN_USER_HOUR);

        verify(room, never()).addRoomBan(any());
    }

    @Test
    void persistsAndPublishesAnAuthorizedOnlineBan() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        try (RoomJdbcTestSupport.InstalledDatabase ignored = RoomJdbcTestSupport.install(dataSource)) {
            RoomManager manager = new RoomManager(false);
            Room room = activeRoom(manager, 41, 7);
            onlineTarget(8, "target");

            manager.banUserFromRoom(null, 8, 41, RoomManager.RoomBanTypes.RWUAM_BAN_USER_HOUR);

            verify(room).addRoomBan(any(RoomBan.class));
            assertEquals(
                    List.of(Map.of(1, 41, 2, 8)),
                    dataSource.calls().stream()
                            .filter(call -> call.sql().startsWith("INSERT INTO room_bans"))
                            .map(call -> Map.of(1, call.parameters().get(1), 2, call.parameters().get(2)))
                            .toList());
        }
    }

    private Room activeRoom(RoomManager manager, int id, int ownerId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        when(room.getOwnerId()).thenReturn(ownerId);
        manager.registerActiveRoom(room);
        return room;
    }

    private Habbo onlineTarget(int id, String username) {
        Habbo target = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getUsername()).thenReturn(username);
        when(target.getHabboInfo()).thenReturn(info);
        when(this.habbos.getHabbo(id)).thenReturn(target);
        return target;
    }
}
