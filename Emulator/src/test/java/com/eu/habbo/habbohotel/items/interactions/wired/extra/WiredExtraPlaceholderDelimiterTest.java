package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** AIR's username and furni name placeholders: the delimiter starts empty and takes at most 5 characters. */
class WiredExtraPlaceholderDelimiterTest {

    private static ResultSet stored(String wiredData) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    private static WiredSettings settings(int multiple, String text) {
        return new WiredSettings(new int[] {multiple}, text, new int[0], -1);
    }

    @Test
    void theUsernameDelimiterIsCutToFiveOnSave() {
        WiredExtraTextOutputUsername box = new WiredExtraTextOutputUsername(1, 1, mock(Item.class), "", 0, 0);
        box.saveData(settings(1, "names\t -- and -- "), null);

        assertEquals(" -- a", box.getDelimiter());
        LegacyDefinition definition = LegacyDefinition.of(box, null);
        assertEquals("names\t -- a", definition.stringParam());
        assertArrayEquals(new int[] {1}, definition.intParams());
    }

    @Test
    void aSingleNameSendsTheNameAloneAndDefaultsToNoDelimiter() {
        WiredExtraTextOutputUsername box = new WiredExtraTextOutputUsername(1, 1, mock(Item.class), "", 0, 0);
        box.saveData(settings(0, "who"), null);

        assertEquals("", box.getDelimiter());
        assertEquals("who", LegacyDefinition.of(box, null).stringParam());
    }

    @Test
    void anOldUsernameBoxKeepsItsLongDelimiterUntilSaved() throws Exception {
        WiredExtraTextOutputUsername box = new WiredExtraTextOutputUsername(1, 1, mock(Item.class), "", 0, 0);
        box.loadWiredData(
                stored("{\"placeholderName\":\"all\",\"placeholderType\":2,\"delimiter\":\" and also \","
                        + "\"userSource\":0}"),
                null);

        assertEquals(" and also ", box.getDelimiter());
        assertEquals("all\t and also ", LegacyDefinition.of(box, null).stringParam());

        box.saveData(settings(1, LegacyDefinition.of(box, null).stringParam()), null);
        assertEquals(" and ", box.getDelimiter());
    }

    @Test
    void theFurniNameDelimiterFollowsTheSameRules() throws Exception {
        WiredExtraTextOutputFurniName box = new WiredExtraTextOutputFurniName(1, 1, mock(Item.class), "", 0, 0);
        box.loadWiredData(
                stored("{\"placeholderName\":\"furni\",\"placeholderType\":2,\"delimiter\":\", then \","
                        + "\"furniSource\":0}"),
                mock(Room.class));
        assertEquals(", then ", box.getDelimiter());

        box.saveData(settings(1, "furni\t, then "), null);
        assertEquals(", the", box.getDelimiter());
        assertEquals("furni\t, the", LegacyDefinition.of(box, mock(Room.class)).stringParam());

        box.saveData(settings(0, "furni"), null);
        assertEquals("", box.getDelimiter());
        assertEquals("furni", LegacyDefinition.of(box, mock(Room.class)).stringParam());
    }
}
