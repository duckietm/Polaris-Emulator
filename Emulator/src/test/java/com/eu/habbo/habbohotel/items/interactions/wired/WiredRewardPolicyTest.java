package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveCredits;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

/**
 * The boxes that grant value out of nothing refuse to be configured without the permission.
 *
 * <p>The per-execution cap bounds one firing; it does not bound a repeater wired to a give-credits
 * box in a room full of people. Before this gate the only boundary was which furni the catalogue
 * sold, which is data rather than code and so outside anything a test can hold.
 */
class WiredRewardPolicyTest {

    @Test
    void grantingValueNeedsThePermission() {
        assertFalse(WiredRewardPolicy.canConfigure(author(false)), "a player must not configure a value grant");
        assertTrue(WiredRewardPolicy.canConfigure(author(true)), "a superwired author may");
    }

    @Test
    void aMissingClientIsRefused() {
        assertFalse(WiredRewardPolicy.canConfigure(null), "no author means no permission to check");
    }

    @Test
    void theEffectRefusesToSaveWithoutIt() {
        WiredEffectGiveCredits effect = new WiredEffectGiveCredits(1, 7, mock(Item.class), "0", 0, 0);
        WiredSettings settings = new WiredSettings(new int[] {WiredSourceUtil.SOURCE_TRIGGER}, "500", new int[0], 0);

        assertFalse(effect.saveData(settings, author(false)), "a player's save must be rejected");
        assertTrue(effect.saveData(settings, author(true)), "a superwired author's save goes through");
    }

    private static GameClient author(boolean superwired) {
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.hasPermission(Permission.ACC_SUPERWIRED)).thenReturn(superwired);
        return client;
    }
}
