package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.boxBase;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.context;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.passes;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.room;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.settings;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import org.junit.jupiter.api.Test;

/** "Trigger cooldown": a user passes, then not again until the configured seconds have gone by. */
class WiredConditionUserCooldownTest {

    @Test
    void aUserPassesOnceAndThenWaitsOutTheCooldown() {
        Room room = room(1);
        RoomUnit actor = user(room, 10, 1);
        long[] now = {1_000_000L};
        WiredConditionUserCooldown box = new WiredConditionUserCooldown(1, 1, boxBase(), "", 0, 0);
        box.clock(() -> now[0]);
        box.saveData(settings(new int[] {30}));

        assertTrue(passes(box, context(room, actor)));
        assertFalse(box.evaluate(context(room, actor)));
        now[0] += 29_999L;
        assertFalse(box.evaluate(context(room, actor)));
        now[0] += 1L;
        assertTrue(passes(box, context(room, actor)));
    }

    @Test
    void theCooldownIsPerUser() {
        Room room = room(1);
        RoomUnit first = user(room, 10, 1);
        RoomUnit second = user(room, 11, 1);
        WiredConditionUserCooldown box = new WiredConditionUserCooldown(1, 1, boxBase(), "", 0, 0);
        box.clock(() -> 5_000L);
        box.saveData(settings(new int[] {60}));

        assertTrue(passes(box, context(room, first)));
        assertTrue(passes(box, context(room, second)));
        assertFalse(box.evaluate(context(room, first)));
    }

    @Test
    void withoutAUserItNeverPasses() {
        WiredConditionUserCooldown box = new WiredConditionUserCooldown(1, 1, boxBase(), "", 0, 0);
        box.saveData(settings(new int[] {10}));

        assertFalse(box.evaluate(context(room(1), null)));
    }

    @Test
    void theSecondsAreClampedToSomethingSensible() {
        WiredConditionUserCooldown box = new WiredConditionUserCooldown(1, 1, boxBase(), "", 0, 0);

        box.saveData(settings(new int[] {0}));
        assertEquals(WiredConditionUserCooldown.MIN_SECONDS, box.getSeconds());
        box.saveData(settings(new int[] {99_999_999}));
        assertEquals(WiredConditionUserCooldown.MAX_SECONDS, box.getSeconds());
        box.saveData(settings(new int[] {}));
        assertEquals(WiredConditionUserCooldown.MIN_SECONDS, box.getSeconds());
    }
}
