package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

class RoomModerationBehaviorTest {

    private GameEnvironment originalEnvironment;
    private GameEnvironment environment;
    private UserManager users;

    @BeforeEach
    void installEnvironment() throws Exception {
        Field field = Emulator.class.getDeclaredField("gameEnvironment");
        field.setAccessible(true);
        this.originalEnvironment = (GameEnvironment) field.get(null);
        this.environment = mock(GameEnvironment.class);
        this.users = mock(UserManager.class);
        when(this.environment.getUserManager()).thenReturn(this.users);
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
        User moderator = mock(User.class);
        RoomRightsManager rights = mock(RoomRightsManager.class);
        when(rights.canBan(moderator)).thenReturn(false);
        when(room.getRightsManager()).thenReturn(rights);

        manager.banUserFromRoom(moderator, 8, 41, RoomManager.RoomBanTypes.RWUAM_BAN_USER_HOUR);
        manager.banUserFromRoom(null, 7, 41, RoomManager.RoomBanTypes.RWUAM_BAN_USER_HOUR);

        verify(this.environment, never()).getUserManager();
        verify(room, never()).addRoomBan(any());
    }

    @Test
    void rejectsOnlineUnkickableUsersWithoutPersistingABan() {
        RoomManager manager = new RoomManager(false);
        Room room = activeRoom(manager, 41, 7);
        User target = onlineTarget(8, "protected");
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
                            .map(call -> Map.of(
                                    1,
                                    call.parameters().get(1),
                                    2,
                                    call.parameters().get(2)))
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

    private User onlineTarget(int id, String username) {
        User target = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getUsername()).thenReturn(username);
        when(target.getUserInfo()).thenReturn(info);
        when(this.users.getUser(id)).thenReturn(target);
        return target;
    }
}
