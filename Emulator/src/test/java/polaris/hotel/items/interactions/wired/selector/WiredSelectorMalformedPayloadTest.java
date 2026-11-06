package polaris.hotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;

/**
 * The selector payload guard answers null for a row it cannot parse. A selector reading that row
 * must come up as a fresh box, not throw halfway through the room load and not keep whatever it
 * held before the corrupt row was read.
 */
class WiredSelectorMalformedPayloadTest {

    private static final String TRUNCATED = "{\"rootX\":1,\"rootY\":2,\"width";

    @Test
    void usersAreaResetsOnACorruptRow() throws Exception {
        WiredEffectUsersArea box = new WiredEffectUsersArea(1, 1, base(), "", 0, 0);
        box.loadWiredData(
                row("{\"rootX\":1,\"rootY\":2,\"width\":3,\"height\":4,\"filterExisting\":true,\"invert\":true,"
                        + "\"delay\":5}"),
                null);

        assertResetOnCorruptRow(box, new WiredEffectUsersArea(2, 1, base(), "", 0, 0));
    }

    @Test
    void furniAreaResetsOnACorruptRow() throws Exception {
        WiredEffectFurniArea box = new WiredEffectFurniArea(1, 1, base(), "", 0, 0);
        box.loadWiredData(
                row("{\"rootX\":1,\"rootY\":2,\"width\":3,\"height\":4,\"filterExisting\":true,\"invert\":true,"
                        + "\"delay\":5}"),
                null);

        assertResetOnCorruptRow(box, new WiredEffectFurniArea(2, 1, base(), "", 0, 0));
    }

    @Test
    void furniByTypeResetsOnACorruptRow() throws Exception {
        WiredEffectFurniByType box = new WiredEffectFurniByType(1, 1, base(), "", 0, 0);
        box.loadWiredData(
                row("{\"sourceType\":1,\"matchState\":true,\"filterExisting\":true,\"invert\":true,"
                        + "\"pickedFurniIds\":[7],\"delay\":5}"),
                null);

        assertResetOnCorruptRow(box, new WiredEffectFurniByType(2, 1, base(), "", 0, 0));
    }

    @Test
    void usersNeighborhoodResetsOnACorruptRow() throws Exception {
        WiredEffectUsersNeighborhood box = new WiredEffectUsersNeighborhood(1, 1, base(), "", 0, 0);
        box.loadWiredData(row(neighborhood()), null);

        assertResetOnCorruptRow(box, new WiredEffectUsersNeighborhood(2, 1, base(), "", 0, 0));
    }

    @Test
    void furniNeighborhoodResetsOnACorruptRow() throws Exception {
        WiredEffectFurniNeighborhood box = new WiredEffectFurniNeighborhood(1, 1, base(), "", 0, 0);
        box.loadWiredData(row(neighborhood()), null);

        assertResetOnCorruptRow(box, new WiredEffectFurniNeighborhood(2, 1, base(), "", 0, 0));
    }

    private static void assertResetOnCorruptRow(InteractionWiredEffect box, InteractionWiredEffect fresh)
            throws Exception {
        // The first load configured the box, so a "reset" that merely skipped the corrupt row would
        // still show up as a difference against the fresh one.
        assertNotEquals(fresh.getWiredData(), box.getWiredData());

        assertDoesNotThrow(() -> box.loadWiredData(row(TRUNCATED), null));

        assertEquals(fresh.getWiredData(), box.getWiredData());
    }

    private static String neighborhood() {
        return "{\"sourceType\":3,\"filterExisting\":true,\"invert\":true,\"targetOffsetX\":1,\"targetOffsetY\":1,"
                + "\"tileOffsets\":[[1,1]],\"pickedFurniIds\":[7],\"delay\":5}";
    }

    private static ResultSet row(String wiredData) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    private static Item base() {
        return mock(Item.class);
    }
}
