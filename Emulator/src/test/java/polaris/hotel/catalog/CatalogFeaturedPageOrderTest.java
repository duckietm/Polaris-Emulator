package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The front page slots go to the client in the order of their slot id, not the hash map's order. */
class CatalogFeaturedPageOrderTest {

    private static CatalogFeaturedPage slot(int id) {
        return slot(id, 0);
    }

    private static CatalogFeaturedPage slot(int id, int expires) {
        return new CatalogFeaturedPage(
                id, "Slot " + id, "catalogue/x.png", CatalogFeaturedPage.Type.PAGE_NAME, expires, "x", 0, "");
    }

    @Test
    void endedSlotsAreNoLongerSent() {
        List<CatalogFeaturedPage> slots = List.of(slot(1, 0), slot(2, 1_000), slot(3, 5_000));
        List<Integer> ids = CatalogFeaturedPage.shown(slots, 2_000).stream()
                .map(CatalogFeaturedPage::getSlotId)
                .toList();
        assertEquals(List.of(1, 3), ids);
    }

    @Test
    void slotsAreSentInSlotOrder() {
        Int2ObjectMap<CatalogFeaturedPage> map = new Int2ObjectOpenHashMap<>();
        for (int id : new int[] {4, 2, 1, 3, 12}) {
            map.put(id, slot(id));
        }
        List<Integer> ids = CatalogFeaturedPage.inSlotOrder(map.values()).stream()
                .map(CatalogFeaturedPage::getSlotId)
                .toList();
        assertEquals(List.of(1, 2, 3, 4, 12), ids);
    }
}
