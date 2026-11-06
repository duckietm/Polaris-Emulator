package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * Whether a reference to a shared variable may write was decided by whoever edited the referring room:
 * a user with wired rights in one room of an owner could write into a variable of another room of the
 * same owner, where they held no rights, for any user id.
 */
class WiredExtraVariableReferenceWriteTest {

    private static GameClient client(int id, boolean anyRoomOwner) {
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        when(user.hasPermission(Permission.ACC_ANYROOMOWNER)).thenReturn(anyRoomOwner);
        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);
        return client;
    }

    @Test
    void onlyTheOwnerOrStaffSaveAWritableReference() {
        Room room = mock(Room.class);
        when(room.getOwnerId()).thenReturn(5);

        assertTrue(WiredExtraVariableReference.mayWriteSource(room, client(5, false)), "the owner");
        assertFalse(WiredExtraVariableReference.mayWriteSource(room, client(9, false)), "a builder with wired rights");
        assertTrue(WiredExtraVariableReference.mayWriteSource(room, client(12, true)), "staff who own any room");
        assertFalse(WiredExtraVariableReference.mayWriteSource(room, null));
    }

    @Test
    void anyoneElseGetsAReadOnlyReference() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/hotel/items/interactions/wired/extra/WiredExtraVariableReference.java"));

        assertTrue(source.contains("this.readOnly = config.readOnly || !mayWriteSource(room, gameClient);"));
    }
}
