package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * The room settings window saves who may kick, mute and ban, but the handlers only asked whether the
 * user held rights: "owner only" never applied, and "everyone may kick" never applied either.
 */
class RoomModerationOptionsTest {

    private static final int OWNER = 1;
    private static final int RIGHTS = 2;
    private static final int VISITOR = 3;

    private static User user(int id) {
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }

    private static RoomRightsManager rights(int mute, int kick, int ban) {
        Room room = mock(Room.class);
        when(room.getOwnerId()).thenReturn(OWNER);
        when(room.getMuteOption()).thenReturn(mute);
        when(room.getKickOption()).thenReturn(kick);
        when(room.getBanOption()).thenReturn(ban);
        return new RoomRightsManager(
                room, IntArrayList.of(RIGHTS), new Int2ObjectOpenHashMap<>(), new Int2IntOpenHashMap());
    }

    @Test
    void ownerOnlyKeepsRightsHoldersOut() {
        RoomRightsManager manager = rights(0, 0, 0);

        assertTrue(manager.canKick(user(OWNER)));
        assertTrue(manager.canMute(user(OWNER)));
        assertTrue(manager.canBan(user(OWNER)));
        assertFalse(manager.canKick(user(RIGHTS)));
        assertFalse(manager.canMute(user(RIGHTS)));
        assertFalse(manager.canBan(user(RIGHTS)));
    }

    @Test
    void rightsHoldersMayWhenTheRoomSaysSo() {
        RoomRightsManager manager = rights(1, 1, 1);

        assertTrue(manager.canKick(user(RIGHTS)));
        assertTrue(manager.canMute(user(RIGHTS)));
        assertTrue(manager.canBan(user(RIGHTS)));
        assertFalse(manager.canKick(user(VISITOR)));
    }

    @Test
    void everyoneMayKickButNotMuteOrBan() {
        RoomRightsManager manager = rights(2, 2, 2);

        assertTrue(manager.canKick(user(VISITOR)));
        assertFalse(manager.canMute(user(VISITOR)), "mute has no everyone level");
        assertFalse(manager.canBan(user(VISITOR)), "ban has no everyone level");
        assertTrue(manager.canMute(user(RIGHTS)), "a stored 2 still lets rights holders mute");
    }

    @Test
    void staffWhoOwnAnyRoomAlwaysMay() {
        User staff = user(VISITOR);
        when(staff.hasPermission(Permission.ACC_ANYROOMOWNER)).thenReturn(true);

        assertTrue(rights(0, 0, 0).canBan(staff));
    }

    @Test
    void theHandlersAskTheRoomSetting() throws Exception {
        Path base = Path.of("src/main/java/polaris");

        assertTrue(Files.readString(base.resolve("messages/incoming/rooms/users/RoomUserKickEvent.java"))
                .contains("room.getRightsManager().canKick(this.client.getUser())"));
        assertTrue(Files.readString(base.resolve("messages/incoming/rooms/users/RoomUserMuteEvent.java"))
                .contains("room.getRightsManager().canMute(this.client.getUser())"));
        assertTrue(Files.readString(base.resolve("hotel/rooms/RoomModerationService.java"))
                .contains("room.getRightsManager().canBan(rights)"));
    }
}
