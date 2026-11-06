package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.permissions.PermissionsManager;
import polaris.hotel.permissions.Rank;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.incoming.wired.WiredSaveException;

/**
 * Give Effect applied any effect id, so a room owner could dress themselves or visitors in the staff or
 * ambassador effect, which :enable and the effect packet refuse to ranks below theirs.
 */
class WiredEffectGiveEffectRankTest {

    private static final int STAFF_EFFECT = 102;

    private static User user(int rankId) {
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(rankId);
        UserInfo info = mock(UserInfo.class);
        when(info.getRank()).thenReturn(rank);
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }

    private static MockedStatic<WiredPlatform> platform() {
        PermissionsManager permissions = mock(PermissionsManager.class);
        when(permissions.isEffectBlocked(STAFF_EFFECT, 1)).thenReturn(true);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getPermissionsManager()).thenReturn(permissions);
        MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class);
        platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
        return platform;
    }

    @Test
    void aReservedEffectIsNotGivenToALowerRank() {
        try (MockedStatic<WiredPlatform> ignored = platform()) {
            assertTrue(WiredEffectGiveEffect.blockedFor(STAFF_EFFECT, user(1)));
            assertFalse(WiredEffectGiveEffect.blockedFor(STAFF_EFFECT, user(7)));
            assertFalse(WiredEffectGiveEffect.blockedFor(4, user(1)), "an ordinary effect stays open");
            assertFalse(WiredEffectGiveEffect.blockedFor(STAFF_EFFECT, null), "bots and pets have no rank");
        }
    }

    @Test
    void aBoxCannotBeSavedWithAnEffectAboveItsEditor() {
        try (MockedStatic<WiredPlatform> ignored = platform()) {
            GameClient owner = mock(GameClient.class);
            User ownerUser = user(1);
            when(owner.getUser()).thenReturn(ownerUser);
            GameClient staff = mock(GameClient.class);
            User staffUser = user(7);
            when(staff.getUser()).thenReturn(staffUser);
            WiredEffectGiveEffect box = new WiredEffectGiveEffect(1, 1, base(), "", 0, 0);
            WiredSettings settings = new WiredSettings(new int[] {STAFF_EFFECT, 0, 0}, "", new int[0], 0);

            assertThrows(WiredSaveException.class, () -> box.saveData(settings, owner));
            assertDoesNotThrow(() -> box.saveData(settings, staff));
        }
    }
}
