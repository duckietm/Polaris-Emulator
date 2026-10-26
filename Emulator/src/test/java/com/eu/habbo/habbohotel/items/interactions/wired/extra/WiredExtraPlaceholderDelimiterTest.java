package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * AIR's username and furni name placeholders: the delimiter starts empty and takes at most 5 characters; a longer one
 * is refused, not cut (observed on AIR WIN63-202610201032).
 */
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
    void aUsernameDelimiterOfAtMostFiveIsSavedAndALongerOneRefused() throws Exception {
        WiredExtraTextOutputUsername box = new WiredExtraTextOutputUsername(1, 1, mock(Item.class), "", 0, 0);
        box.saveData(settings(1, "names\t and "), null);

        assertEquals(" and ", box.getDelimiter());
        LegacyDefinition definition = LegacyDefinition.of(box, null);
        assertEquals("names\t and ", definition.stringParam());
        assertArrayEquals(new int[] {1}, definition.intParams());

        WiredSaveException refused =
                assertThrows(WiredSaveException.class, () -> box.saveData(settings(1, "names\t -- and -- "), null));
        assertEquals("wiredfurni.error.placeholder.delimiter_length", refused.getMessage());
    }

    @Test
    void aSingleNameSendsTheNameAloneAndDefaultsToNoDelimiter() throws Exception {
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
    }

    @Test
    void theFurniNameFollowsTheSameRules() throws Exception {
        WiredExtraTextOutputFurniName box = new WiredExtraTextOutputFurniName(1, 1, mock(Item.class), "", 0, 0);
        box.loadWiredData(
                stored("{\"placeholderName\":\"furni\",\"placeholderType\":2,\"delimiter\":\", then \","
                        + "\"furniSource\":0}"),
                mock(Room.class));
        assertEquals(", then ", box.getDelimiter());

        assertThrows(WiredSaveException.class, () -> box.saveData(settings(1, "furni\t, then "), null));
        box.saveData(settings(1, "Furni\t, the"), null);
        assertEquals("furni\t, the", LegacyDefinition.of(box, mock(Room.class)).stringParam());

        box.saveData(settings(0, "furni"), null);
        assertEquals("", box.getDelimiter());
        assertEquals("furni", LegacyDefinition.of(box, mock(Room.class)).stringParam());
    }

    @Test
    void namesAreRefusedForLengthSyntaxAndTheReservedName() {
        WiredExtraTextOutputUsername box = new WiredExtraTextOutputUsername(1, 1, mock(Item.class), "", 0, 0);
        String fortyOne = "a".repeat(41);
        for (String[] refusal : new String[][] {
            {fortyOne, "name_length"}, {"a b", "name_syntax"}, {"a-b", "name_syntax"}, {"trigger_text", "reserved"}
        }) {
            WiredSaveException refused =
                    assertThrows(WiredSaveException.class, () -> box.saveData(settings(0, refusal[0]), null));
            assertEquals("wiredfurni.error.placeholder." + refusal[1], refused.getMessage());
        }
        WiredSaveException empty =
                assertThrows(WiredSaveException.class, () -> box.saveData(settings(1, "all\t"), null));
        assertEquals("wiredfurni.error.placeholder.delimiter_empty", empty.getMessage());
    }
}
