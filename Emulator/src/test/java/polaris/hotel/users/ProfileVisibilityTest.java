package polaris.hotel.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.permissions.Permission;

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
        User owner = viewer(7, false);
        User stranger = viewer(8, false);
        User moderator = viewer(9, true);

        assertFalse(ProfileVisibility.hidesDetailsFrom(owner, 7, true));
        assertTrue(ProfileVisibility.hidesDetailsFrom(stranger, 7, true));
        assertFalse(ProfileVisibility.hidesDetailsFrom(moderator, 7, true));
        assertFalse(ProfileVisibility.hidesDetailsFrom(stranger, 7, false));
    }

    @Test
    void onlineOwnersAreReadFromTheirSettings() {
        User owner = mock(User.class);
        UserStats stats = mock(UserStats.class);
        when(owner.getUserStats()).thenReturn(stats);

        stats.hideProfile = true;
        assertTrue(ProfileVisibility.isProfileHidden(owner, 7));

        stats.hideProfile = false;
        assertFalse(ProfileVisibility.isProfileHidden(owner, 7));
    }

    private static User viewer(int id, boolean moderator) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(user.getUserInfo()).thenReturn(info);
        when(info.getId()).thenReturn(id);
        when(user.hasPermission(Permission.ACC_SUPPORTTOOL)).thenReturn(moderator);
        return user;
    }
}
