package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.WiredSettings;

/**
 * "Reset highscores" shows the same delay slider as "reset timers", but its save returned true
 * without keeping the value and it stored nothing, so the delay was always zero after a reopen.
 */
class WiredEffectResetHighscoresTest {

    @Test
    void theDelayIsKeptOnSaveAndStored() throws Exception {
        WiredEffectResetHighscores box = new WiredEffectResetHighscores(1, 1, base(), "", 0, 0);

        box.saveData(new WiredSettings(new int[0], "", new int[0], 0, 5), null);

        assertEquals(5, box.getDelay());
        assertEquals(5, json(box).get("delay").getAsInt());
    }

    @Test
    void theDelaySurvivesAReload() throws Exception {
        WiredEffectResetHighscores saved = new WiredEffectResetHighscores(1, 1, base(), "", 0, 0);
        saved.saveData(new WiredSettings(new int[0], "", new int[0], 0, 5), null);

        WiredEffectResetHighscores loaded = new WiredEffectResetHighscores(2, 1, base(), "", 0, 0);
        loaded.loadWiredData(row(saved.getWiredData()), null);

        assertEquals(5, loaded.getDelay());
    }

    @Test
    void anOldEmptyRowAndABareNumberBothLoad() throws Exception {
        WiredEffectResetHighscores box = new WiredEffectResetHighscores(1, 1, base(), "", 0, 0);

        box.loadWiredData(row(""), null);
        assertEquals(0, box.getDelay());

        box.loadWiredData(row("7"), null);
        assertEquals(7, box.getDelay());
    }
}
