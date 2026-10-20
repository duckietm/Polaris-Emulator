package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A legacy "give reward" row checked for at least one field and then read six; a JSON row with no
 * reward list was handed to addAll as null. Either failed the furni load.
 */
class WiredEffectGiveRewardTest {

    @Test
    void aShortLegacyRowKeepsTheDefaults() throws Exception {
        WiredEffectGiveReward box = new WiredEffectGiveReward(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("5"), null);

        assertEquals(0, box.getLimit());
        assertTrue(box.getRewardItems().isEmpty());
    }

    @Test
    void aRowWithoutRewardsLoadsWithNone() throws Exception {
        WiredEffectGiveReward box = new WiredEffectGiveReward(1, 1, base(), "", 0, 0);

        box.loadWiredData(
                row("{\"limit\":1,\"given\":0,\"reward_time\":0,\"unique_rewards\":false,\"limit_interval\":0,"
                        + "\"delay\":0,\"userSource\":0}"),
                null);

        assertEquals(1, box.getLimit());
        assertTrue(box.getRewardItems().isEmpty());
    }

    @Test
    void aRewardGivenIsSavedWithTheBoxSoARestartDoesNotGiveItAgain() {
        WiredEffectGiveReward box = new WiredEffectGiveReward(1, 1, base(), "", 0, 0);
        assertFalse(box.needsUpdate());

        box.incrementGiven();

        assertEquals(1, box.getGiven());
        assertTrue(box.needsUpdate(), "the new count is written on the next save");
    }
}
