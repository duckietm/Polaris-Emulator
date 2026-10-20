package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** AIR's random add-on ints are {@code [skips, picks]}, 0..100 and 1..100. */
class WiredExtraRandomLayoutTest {

    private static WiredExtraRandom box() {
        return new WiredExtraRandom(1, 1, mock(Item.class), "", 0, 0);
    }

    private static WiredExtraRandom loaded(String wiredData) throws Exception {
        WiredExtraRandom box = box();
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        box.loadWiredData(set, null);
        return box;
    }

    @Test
    void savesAndSendsSkipsThenPicks() {
        WiredExtraRandom box = box();
        box.saveData(new WiredSettings(new int[] {3, 2}, "", new int[0], -1), null);

        assertEquals(3, box.getSkipExecutions());
        assertEquals(2, box.getPickAmount());
        assertArrayEquals(new int[] {3, 2}, LegacyDefinition.of(box, null).intParams());
    }

    @Test
    void clampsToTheOfficialSliders() {
        WiredExtraRandom box = box();
        box.saveData(new WiredSettings(new int[] {500, 0}, "", new int[0], -1), null);

        assertEquals(100, box.getSkipExecutions());
        assertEquals(1, box.getPickAmount());

        box.saveData(new WiredSettings(new int[] {-1, 900}, "", new int[0], -1), null);
        assertEquals(0, box.getSkipExecutions());
        assertEquals(100, box.getPickAmount());
    }

    @Test
    void anOldBoxKeepsItsAmountsWithinTheNewRange() throws Exception {
        WiredExtraRandom box = loaded("{\"skipTop\":2,\"pickAmount\":4,\"skipExecutions\":3}");
        assertEquals(4, box.getPickAmount());
        assertEquals(3, box.getSkipExecutions());
        assertArrayEquals(new int[] {3, 4}, LegacyDefinition.of(box, null).intParams());
        assertFalse(box.getWiredData().contains("skipTop"));

        WiredExtraRandom large = loaded("{\"pickAmount\":700,\"skipExecutions\":1000}");
        assertEquals(100, large.getPickAmount());
        assertEquals(100, large.getSkipExecutions());
    }

    @Test
    void theStoredDataRoundTrips() throws Exception {
        WiredExtraRandom box = box();
        box.saveData(new WiredSettings(new int[] {5, 7}, "", new int[0], -1), null);

        WiredExtraRandom reloaded = loaded(box.getWiredData());
        assertEquals(5, reloaded.getSkipExecutions());
        assertEquals(7, reloaded.getPickAmount());
    }
}
