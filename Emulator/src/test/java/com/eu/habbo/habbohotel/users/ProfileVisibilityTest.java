package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.permissions.Permission;
import org.junit.jupiter.api.Test;

class ProfileVisibilityTest {

    @Test
    void onlyAHiddenProfileViewedBySomebodyElseIsCut() {
        assertFalse(ProfileVisibility.hidesDetails(false, false, false));
        assertTrue(ProfileVisibility.hidesDetails(true, false, false));
        assertFalse(ProfileVisibility.hidesDetails(true, true, false), "the owner sees the full profile");
        assertFalse(ProfileVisibility.hidesDetails(true, false, true), "moderators see the full profile");
    }

    @Test
    void viewerChecksUseTheOwnerIdAndTheSupportToolPermission() {
        Habbo owner = viewer(7, false);
        Habbo stranger = viewer(8, false);
        Habbo moderator = viewer(9, true);

        assertFalse(ProfileVisibility.hidesDetailsFrom(owner, 7, true));
        assertTrue(ProfileVisibility.hidesDetailsFrom(stranger, 7, true));
        assertFalse(ProfileVisibility.hidesDetailsFrom(moderator, 7, true));
        assertFalse(ProfileVisibility.hidesDetailsFrom(stranger, 7, false));
    }

    @Test
    void onlineOwnersAreReadFromTheirSettings() {
        Habbo owner = mock(Habbo.class);
        HabboStats stats = mock(HabboStats.class);
        when(owner.getHabboStats()).thenReturn(stats);

        stats.hideProfile = true;
        assertTrue(ProfileVisibility.isProfileHidden(owner, 7));

        stats.hideProfile = false;
        assertFalse(ProfileVisibility.isProfileHidden(owner, 7));
    }

    private static Habbo viewer(int id, boolean moderator) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(id);
        when(habbo.hasPermission(Permission.ACC_SUPPORTTOOL)).thenReturn(moderator);
        return habbo;
    }
}
