package com.eu.habbo.messages.incoming.soundboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import org.junit.jupiter.api.Test;

class SoundboardAuthorizationContractTest {

    @Test
    void catalogManagementRequiresItsDedicatedPermission() {
        Habbo habbo = mock(Habbo.class);
        when(habbo.hasPermission("acc_soundboard_manage")).thenReturn(true);

        assertTrue(SoundboardManagementAccess.canManage(habbo));

        when(habbo.hasPermission("acc_soundboard_manage")).thenReturn(false);
        assertFalse(SoundboardManagementAccess.canManage(habbo));
        assertFalse(SoundboardManagementAccess.canManage(null));
    }

    @Test
    void roomToggleAllowsTheOwnerOrAnyRoomOwnerPermissionOnly() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Room room = mock(Room.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(42);
        when(room.getOwnerId()).thenReturn(42);

        assertTrue(SoundboardSetEnabledEvent.canToggle(habbo, room));

        when(room.getOwnerId()).thenReturn(7);
        when(habbo.hasPermission("acc_anyroomowner")).thenReturn(true);
        assertTrue(SoundboardSetEnabledEvent.canToggle(habbo, room));

        when(habbo.hasPermission("acc_anyroomowner")).thenReturn(false);
        when(habbo.hasPermission("acc_supporttool")).thenReturn(true);
        assertFalse(SoundboardSetEnabledEvent.canToggle(habbo, room));
    }

    @Test
    void reorderPayloadCountIsBounded() {
        assertTrue(SoundboardCatalogReorderEvent.isCountAllowed(0));
        assertTrue(SoundboardCatalogReorderEvent.isCountAllowed(500));
        assertFalse(SoundboardCatalogReorderEvent.isCountAllowed(-1));
        assertFalse(SoundboardCatalogReorderEvent.isCountAllowed(501));
    }
}
