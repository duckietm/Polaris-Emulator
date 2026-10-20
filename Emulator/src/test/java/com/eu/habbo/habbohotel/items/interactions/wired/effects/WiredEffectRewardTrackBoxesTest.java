package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectProgressAchievementTest.author;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredHotelProgress;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredActionCodes;
import org.junit.jupiter.api.Test;

/** Habbo's progress and reset reward-track actions: what they accept, store and send back. */
class WiredEffectRewardTrackBoxesTest {

    private static WiredEffectProgressRewardTrack progressBox() {
        return new WiredEffectProgressRewardTrack(11, 1, base(), "", 0, 0);
    }

    private static WiredEffectResetRewardTrack resetBox() {
        return new WiredEffectResetRewardTrack(12, 1, base(), "", 0, 0);
    }

    private static WiredSettings settings(String text, int... params) {
        return new WiredSettings(params, text, new int[0], 0);
    }

    /** The official user source slot. */
    private static WiredSettings userSlot(WiredSettings settings, int source) {
        settings.setUserSourceTypes(new int[] {source});
        return settings;
    }

    @Test
    void onlyAStaffAuthorMaySaveEitherBox() {
        assertFalse(progressBox().saveData(settings("season_1\tgames", 1, 5, 0), author(false)));
        assertFalse(progressBox().saveData(settings("season_1\tgames", 1, 5, 0), null));
        assertTrue(progressBox().saveData(settings("season_1\tgames", 1, 5, 0), author(true)));

        assertFalse(resetBox().saveData(settings("season_1", 0), author(false)));
        assertFalse(resetBox().saveData(settings("season_1", 0), null));
        assertTrue(resetBox().saveData(settings("season_1", 0), author(true)));
    }

    @Test
    void theProgressBoxNeedsATrackAndATask() {
        WiredEffectProgressRewardTrack box = progressBox();

        assertFalse(box.saveData(settings("season_1", 1, 5, 0), author(true)), "no task");
        assertFalse(box.saveData(settings("season_1\t", 1, 5, 0), author(true)));
        assertFalse(box.saveData(settings("\tgames", 1, 5, 0), author(true)));
        assertFalse(
                box.saveData(settings("season_1\tgames\textra", 1, 5, 0), author(true)),
                "the third field is a variable id");
        assertFalse(
                box.saveData(settings("season_1\tgames", 1, 1, 5, -10), author(true)), "a value variable needs its id");
        assertFalse(
                box.saveData(settings("season:1\tgames", 1, 5, 0), author(true)), "':' is the allow-list separator");
        assertFalse(box.saveData(settings("x".repeat(101) + "\tgames", 1, 5, 0), author(true)), "AIR: at most 100");
        assertFalse(box.saveData(settings("season.1\tgames", 1, 5, 0), author(true)), "AIR: [a-zA-Z0-9_] only");
        assertFalse(box.saveData(settings("season-1\tgames", 1, 5, 0), author(true)));
        assertFalse(box.saveData(settings("s\u00e9ason\tgames", 1, 5, 0), author(true)), "ASCII letters only");
        assertTrue(box.saveData(settings("x".repeat(100) + "\t" + "y".repeat(100), 1, 5, 0), author(true)));
        assertEquals("x".repeat(100), box.getTrackId());
    }

    @Test
    void progressBoxesSavedUnderTheOlderIdRulesKeepTheirTrack() throws Exception {
        WiredEffectProgressRewardTrack loaded = progressBox();
        loaded.loadWiredData(
                row("{\"trackId\":\"season.1\",\"taskId\":\"games-2\",\"amount\":3,\"delay\":0,\"userSource\":0}"),
                null);
        assertEquals("season.1", loaded.getTrackId());
        assertEquals("games-2", loaded.getTaskId());
    }

    @Test
    void theProgressParamsAreClampedAndSentBackUnderItsCode() {
        WiredEffectProgressRewardTrack box = progressBox();
        assertTrue(box.saveData(settings(" season_1 \t games ", 0, 0, Integer.MAX_VALUE, 999), author(true)));

        assertEquals("season_1", box.getTrackId());
        assertEquals("games", box.getTaskId());
        assertFalse(box.isAddToExisting());
        assertEquals(WiredHotelProgress.MAX_AMOUNT, box.getAmount());
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, box.getUserSource());

        box.saveData(
                userSlot(settings("season_1\tgames", 1, 0, -4, -20), WiredSourceUtil.SOURCE_SELECTOR), author(true));
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, box.getUserSource());
        var body = WiredEffectProgressAchievementTest.body(box);
        assertEquals("season_1\tgames", body.text());
        // official [add, amount from a variable, amount, variable target]
        assertArrayEquals(new int[] {1, 0, 1, -20}, body.params());

        box.saveData(settings("season_1\tgames\t12", 0, 1, 5, -10), author(true));
        body = WiredEffectProgressAchievementTest.body(box);
        // AIR: the ids as the string, the amount's variable as the variable id.
        assertEquals("season_1\tgames", body.text());
        assertArrayEquals(new String[] {"room:12"}, box.officialVariableIds());
        assertArrayEquals(new int[] {0, 1, 5, -10}, body.params());
        assertEquals(WiredActionCodes.official(WiredEffectType.PROGRESS_REWARD_TRACK.code), body.code());
    }

    @Test
    void theProgressBoxSurvivesTheDatabaseAndTamperedRowsAreCleaned() throws Exception {
        WiredEffectProgressRewardTrack saved = progressBox();
        saved.saveData(
                userSlot(settings("season_1\tgames", 0, 0, 30, -10), WiredSourceUtil.SOURCE_SELECTOR), author(true));

        WiredEffectProgressRewardTrack loaded = progressBox();
        loaded.loadWiredData(row(saved.getWiredData()), null);
        assertEquals("season_1", loaded.getTrackId());
        assertEquals("games", loaded.getTaskId());
        assertFalse(loaded.isAddToExisting());
        assertEquals(30, loaded.getAmount());
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, loaded.getUserSource());

        WiredEffectProgressRewardTrack tampered = progressBox();
        tampered.loadWiredData(
                row(
                        "{\"trackId\":\"a b\",\"taskId\":\"ok\",\"addToExisting\":true,\"amount\":-9,\"delay\":0,\"userSource\":5}"),
                null);
        assertEquals("", tampered.getTrackId());
        assertEquals(1, tampered.getAmount());
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, tampered.getUserSource());
    }

    @Test
    void theResetBoxTakesOneTrackAndItsUserSource() throws Exception {
        WiredEffectResetRewardTrack box = resetBox();

        assertFalse(box.saveData(settings("season\t1", 0), author(true)), "AIR: anything but a tab");
        assertFalse(box.saveData(settings("x".repeat(101), 0), author(true)), "AIR: at most 100");
        assertTrue(box.saveData(settings("", 0), author(true)), "AIR does not ask for a filled-in id");
        assertEquals("", box.getTrackId());
        assertTrue(box.saveData(settings("season 1.b-c", 0), author(true)));
        assertEquals("season 1.b-c", box.getTrackId());
        WiredEffectResetRewardTrack reloaded = resetBox();
        reloaded.loadWiredData(row(box.getWiredData()), null);
        assertEquals("season 1.b-c", reloaded.getTrackId());
        WiredSettings settings = settings("season_1");
        settings.setUserSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTOR});
        assertTrue(box.saveData(settings, author(true)));

        var body = WiredEffectProgressAchievementTest.body(box);
        assertEquals("season_1", body.text());
        assertArrayEquals(new int[0], body.params()); // official: no ints; the source is the slot
        assertEquals(WiredActionCodes.official(WiredEffectType.RESET_REWARD_TRACK.code), body.code());

        WiredEffectResetRewardTrack loaded = resetBox();
        loaded.loadWiredData(row(box.getWiredData()), null);
        assertEquals("season_1", loaded.getTrackId());
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, loaded.getUserSource());

        loaded.onPickUp();
        assertEquals("", loaded.getTrackId());
    }
}
