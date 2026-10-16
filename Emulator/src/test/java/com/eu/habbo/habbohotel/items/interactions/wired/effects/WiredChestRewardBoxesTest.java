package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import org.junit.jupiter.api.Test;

/** The two chest boxes on the official chest reward layout, and what rows saved before it keep. */
class WiredChestRewardBoxesTest {

    private static WiredSettings settings(String popup, String[] variableIds, int userSlot, int... params) {
        WiredSettings settings =
                new WiredSettings(params, popup, new int[0], 0); // body() reads a definition without picks
        settings.setVariableIds(variableIds);
        settings.setUserSourceTypes(new int[] {userSlot});
        return settings;
    }

    @Test
    void theCurrencyBoxSavesAndSendsBackTheOfficialLayout() throws Exception {
        WiredEffectGiveCurrencyFromChest box = new WiredEffectGiveCurrencyFromChest(1, 1, base(), "", 0, 0);

        box.saveData(
                settings("Well done!\nline 2\nline 3\nline 4", new String[] {"55"}, 200, 0, 25, 1, -20, 1, 13), null);

        var body = WiredEffectProgressAchievementTest.body(box);
        assertArrayEquals(new int[] {0, 25, 1, -20, 1, 13}, body.params());
        assertEquals("Well done!\nline 2\nline 3", body.text(), "three lines at most");
        assertArrayEquals(new String[] {"55"}, box.officialVariableIds());
        assertArrayEquals(new int[] {200, 200}, box.officialUserSources());
    }

    @Test
    void aVariableAmountNeedsItsVariableAndAnUnknownCategoryIsGames() throws Exception {
        WiredEffectGiveCurrencyFromChest box = new WiredEffectGiveCurrencyFromChest(1, 1, base(), "", 0, 0);

        box.saveData(settings("", new String[0], 0, 1, 0, 1, -10, 0, 99), null);

        var body = WiredEffectProgressAchievementTest.body(box);
        assertArrayEquals(new int[] {1, 1, 0, -10, 0, 11}, body.params());
        assertArrayEquals(new String[0], box.officialVariableIds());
    }

    @Test
    void aCurrencyRowFromBeforeKeepsItsAmount() throws Exception {
        WiredEffectGiveCurrencyFromChest box = new WiredEffectGiveCurrencyFromChest(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("{\"amount\":40,\"userSource\":0,\"delay\":0,\"chestIds\":[]}"), null);

        assertArrayEquals(
                new int[] {0, 40, 0, -10, 0, 11},
                WiredEffectProgressAchievementTest.body(box).params());
    }

    @Test
    void theFurniBoxKeepsTheOldestFirstOrderForOldRowsAndSavesTheChosenOne() throws Exception {
        WiredEffectGiveFurniFromChest legacy = new WiredEffectGiveFurniFromChest(1, 1, base(), "", 0, 0);
        legacy.loadWiredData(row("{\"amount\":2,\"userSource\":0,\"delay\":0,\"chestIds\":[]}"), null);
        assertArrayEquals(
                new int[] {0, 2, 0, -10, 0, 1},
                WiredEffectProgressAchievementTest.body(legacy).params());

        WiredEffectGiveFurniFromChest box = new WiredEffectGiveFurniFromChest(1, 1, base(), "", 0, 0);
        box.saveData(settings("", new String[0], 0, 0, 3, 0, -10, 0, 2), null);
        assertArrayEquals(
                new int[] {0, 3, 0, -10, 0, 2},
                WiredEffectProgressAchievementTest.body(box).params());
        assertEquals(2, json(box).get("iterationType").getAsInt());

        WiredEffectGiveFurniFromChest reloaded = new WiredEffectGiveFurniFromChest(1, 1, base(), "", 0, 0);
        reloaded.loadWiredData(row(box.getWiredData()), null);
        assertArrayEquals(
                new int[] {0, 3, 0, -10, 0, 2},
                WiredEffectProgressAchievementTest.body(reloaded).params());
    }
}
