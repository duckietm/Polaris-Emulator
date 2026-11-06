package polaris.hotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.boxBase;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.row;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** A short repeater's row that cannot be read is no configuration; the default delay stands. */
class WiredTriggerRepeaterShortLoadTest {

    @Test
    void anUnreadableRowFallsBackToTheDefaultDelay() throws Exception {
        WiredTriggerRepeaterShort repeater = repeater();

        repeater.loadWiredData(row("{broken"), null);
        assertEquals(WiredTriggerRepeaterShort.DEFAULT_DELAY, storedMillis(repeater));

        repeater.loadWiredData(row("junk"), null);
        assertEquals(WiredTriggerRepeaterShort.DEFAULT_DELAY, storedMillis(repeater));
    }

    @Test
    void aStoredDelayStillLoads() throws Exception {
        WiredTriggerRepeaterShort repeater = repeater();

        repeater.loadWiredData(row("{\"repeatTime\":300}"), null);
        assertEquals(300, storedMillis(repeater));

        repeater.loadWiredData(row("250"), null);
        assertEquals(250, storedMillis(repeater));
    }

    private static WiredTriggerRepeaterShort repeater() {
        return new WiredTriggerRepeaterShort(1, 1, boxBase(), "", 0, 0);
    }

    private static int storedMillis(WiredTriggerRepeaterShort repeater) {
        return JsonParser.parseString(repeater.getWiredData())
                .getAsJsonObject()
                .get("repeatTime")
                .getAsInt();
    }
}
