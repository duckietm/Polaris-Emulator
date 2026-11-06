package polaris.hotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.wired.core.WiredManager;

/**
 * The neighborhood selectors read their source from the official source slot the dialog sends and
 * from the saved row. An unknown source falls back to the trigger, in both directions: stored as-is
 * it would select nothing, silently.
 */
class WiredEffectNeighborhoodSourceTypeTest {

    @Test
    void furniNeighborhoodClampsTheSourceOnSaveAndLoad() throws Exception {
        WiredEffectFurniNeighborhood box = new WiredEffectFurniNeighborhood(1, 1, base(), "", 0, 0);

        box.saveData(furniSaved(999), null);
        assertEquals(3, furniSourceOf(box), "unknown furni source: the trigger furni");

        box.saveData(furniSaved(201), null);
        assertEquals(5, furniSourceOf(box));

        box.loadWiredData(row("{\"sourceType\":-3,\"tileOffsets\":[],\"pickedFurniIds\":[],\"delay\":0}"), null);
        assertEquals(0, furniSourceOf(box));
    }

    @Test
    void usersNeighborhoodClampsTheSourceOnSaveAndLoad() throws Exception {
        WiredEffectUsersNeighborhood box = new WiredEffectUsersNeighborhood(1, 1, base(), "", 0, 0);

        box.saveData(usersSaved(999), null);
        assertEquals(0, usersSourceOf(box));

        box.saveData(usersSaved(201), null);
        assertEquals(1, usersSourceOf(box));

        box.loadWiredData(row("{\"sourceType\":-3,\"tileOffsets\":[],\"pickedFurniIds\":[],\"delay\":0}"), null);
        assertEquals(0, usersSourceOf(box));
    }

    /** A furni-side save with official source {@code source}: furni signal (201) is private 5. */
    private static WiredSettings furniSaved(int source) {
        WiredSettings settings = new WiredSettings(new int[] {0, 0, 0}, "", new int[0], 0);
        settings.setFurniSourceTypes(new int[] {source});
        return settings;
    }

    /** A user-side save with official source {@code source}: user signal (201) is private 1. */
    private static WiredSettings usersSaved(int source) {
        WiredSettings settings = new WiredSettings(new int[] {1, 0, 0}, "", new int[0], 0);
        settings.setUserSourceTypes(new int[] {source});
        return settings;
    }

    private static int furniSourceOf(WiredEffectFurniNeighborhood box) {
        return WiredManager.getGson()
                .fromJson(box.getWiredData(), WiredEffectFurniNeighborhood.JsonData.class)
                .sourceType;
    }

    private static int usersSourceOf(WiredEffectUsersNeighborhood box) {
        return WiredManager.getGson()
                .fromJson(box.getWiredData(), WiredEffectUsersNeighborhood.JsonData.class)
                .sourceType;
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
