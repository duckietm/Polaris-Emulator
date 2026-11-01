package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A bad reward row left the box half saved with its given counter at zero, and saving the box unchanged reset the
 * counter, so the limited rewards were handed out again.
 */
class WiredGiveRewardSaveOrderTest {

    private static String saveData() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/effects/WiredEffectGiveReward.java"));
        int start = source.indexOf("public boolean saveData(");
        return source.substring(start, source.indexOf("\n    }\n", start));
    }

    @Test
    void everyRowIsCheckedBeforeTheBoxChanges() throws Exception {
        String save = saveData();
        int refused = save.indexOf("return false;");

        assertTrue(refused > -1);
        assertTrue(refused < save.indexOf("this.limit = "));
        assertTrue(refused < save.indexOf("this.rewardItems.clear();"));
        assertTrue(refused < save.indexOf("this.given.set(0);"));
    }

    @Test
    void theGivenCounterIsKeptWhenNothingChanged() throws Exception {
        String save = saveData();

        assertTrue(save.indexOf("if (!rewardsChanged) return true;") < save.indexOf("this.given.set(0);"));
        assertTrue(save.indexOf("this.given.set(0);") < save.indexOf("WiredManager.dropRewards(this.getId());"));
    }
}
