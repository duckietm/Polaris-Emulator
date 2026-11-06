package polaris.messages.incoming.soundboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

class SoundboardAuthorizationContractTest {

    @Test
    void catalogManagementRequiresItsDedicatedPermission() {
        User user = mock(User.class);
        when(user.hasPermission("acc_soundboard_manage")).thenReturn(true);

        assertTrue(SoundboardManagementAccess.canManage(user));

        when(user.hasPermission("acc_soundboard_manage")).thenReturn(false);
        assertFalse(SoundboardManagementAccess.canManage(user));
        assertFalse(SoundboardManagementAccess.canManage(null));
    }

    @Test
    void roomToggleAllowsTheOwnerOrAnyRoomOwnerPermissionOnly() {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        Room room = mock(Room.class);
        when(user.getUserInfo()).thenReturn(info);
        when(info.getId()).thenReturn(42);
        when(room.getOwnerId()).thenReturn(42);

        assertTrue(SoundboardSetEnabledEvent.canToggle(user, room));

        when(room.getOwnerId()).thenReturn(7);
        when(user.hasPermission("acc_anyroomowner")).thenReturn(true);
        assertTrue(SoundboardSetEnabledEvent.canToggle(user, room));

        when(user.hasPermission("acc_anyroomowner")).thenReturn(false);
        when(user.hasPermission("acc_supporttool")).thenReturn(true);
        assertFalse(SoundboardSetEnabledEvent.canToggle(user, room));
    }

    @Test
    void reorderPayloadCountIsBounded() {
        assertTrue(SoundboardCatalogReorderEvent.isCountAllowed(0));
        assertTrue(SoundboardCatalogReorderEvent.isCountAllowed(500));
        assertFalse(SoundboardCatalogReorderEvent.isCountAllowed(-1));
        assertFalse(SoundboardCatalogReorderEvent.isCountAllowed(501));
    }
}
