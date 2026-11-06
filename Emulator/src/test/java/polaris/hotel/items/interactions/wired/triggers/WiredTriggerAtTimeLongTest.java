package polaris.hotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.body;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.boxBase;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.room;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.row;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.wired.WiredTriggerType;

/**
 * The long one-shot timer counts in five-second steps, the way the long repeater does, and has a
 * trigger code of its own so the client draws the right dialog for it.
 */
class WiredTriggerAtTimeLongTest {

    @Test
    void reportsItsOwnTriggerType() {
        WiredTriggerAtTimeLong timer = timer();

        assertEquals(WiredTriggerType.AT_GIVEN_TIME_LONG, timer.getType());
        assertEquals(1004, timer.getType().code); // not an official trigger: 1000 and up
        assertEquals(1004, body(timer, room(1)).typeCode());
    }

    @Test
    void aClientUnitIsFiveSeconds() {
        WiredTriggerAtTimeLong timer = timer();

        assertTrue(timer.saveData(new WiredSettings(new int[] {3}, "", new int[0], 0)));

        assertEquals(15_000, storedMillis(timer));
        assertArrayEquals(new int[] {3}, body(timer, room(1)).params());
    }

    @Test
    void storedMillisecondsLoadAsTheyAreAndJunkFallsBackToTheLongDefault() throws Exception {
        WiredTriggerAtTimeLong timer = timer();

        timer.loadWiredData(row("{\"executeTime\":25000}"), null);
        assertEquals(25_000, storedMillis(timer));
        assertArrayEquals(new int[] {5}, body(timer, room(1)).params());

        // Twenty steps of five seconds, as the long repeater falls back to.
        timer.loadWiredData(row("junk"), null);
        assertEquals(100_000, storedMillis(timer));

        // Shorter than one step is not a delay this timer can count.
        timer.loadWiredData(row("{\"executeTime\":2000}"), null);
        assertEquals(100_000, storedMillis(timer));
    }

    private static WiredTriggerAtTimeLong timer() {
        return new WiredTriggerAtTimeLong(1, 1, boxBase(), "", 0, 0);
    }

    private static int storedMillis(WiredTriggerAtTimeLong timer) {
        return JsonParser.parseString(timer.getWiredData())
                .getAsJsonObject()
                .get("executeTime")
                .getAsInt();
    }
}
