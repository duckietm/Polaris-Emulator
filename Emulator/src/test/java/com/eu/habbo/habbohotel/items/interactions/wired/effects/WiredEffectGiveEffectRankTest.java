package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Give Effect applied any effect id, so a room owner could dress themselves or visitors in the staff or
 * ambassador effect, which :enable and the effect packet refuse to ranks below theirs.
 */
class WiredEffectGiveEffectRankTest {

    private static final int STAFF_EFFECT = 102;

    private static Habbo habbo(int rankId) {
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(rankId);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getRank()).thenReturn(rank);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
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
            assertTrue(WiredEffectGiveEffect.blockedFor(STAFF_EFFECT, habbo(1)));
            assertFalse(WiredEffectGiveEffect.blockedFor(STAFF_EFFECT, habbo(7)));
            assertFalse(WiredEffectGiveEffect.blockedFor(4, habbo(1)), "an ordinary effect stays open");
            assertFalse(WiredEffectGiveEffect.blockedFor(STAFF_EFFECT, null), "bots and pets have no rank");
        }
    }

    @Test
    void aBoxCannotBeSavedWithAnEffectAboveItsEditor() {
        try (MockedStatic<WiredPlatform> ignored = platform()) {
            GameClient owner = mock(GameClient.class);
            Habbo ownerHabbo = habbo(1);
            when(owner.getHabbo()).thenReturn(ownerHabbo);
            GameClient staff = mock(GameClient.class);
            Habbo staffHabbo = habbo(7);
            when(staff.getHabbo()).thenReturn(staffHabbo);
            WiredEffectGiveEffect box = new WiredEffectGiveEffect(1, 1, base(), "", 0, 0);
            WiredSettings settings = new WiredSettings(new int[] {STAFF_EFFECT, 0, 0}, "", new int[0], 0);

            assertThrows(WiredSaveException.class, () -> box.saveData(settings, owner));
            assertDoesNotThrow(() -> box.saveData(settings, staff));
        }
    }
}
