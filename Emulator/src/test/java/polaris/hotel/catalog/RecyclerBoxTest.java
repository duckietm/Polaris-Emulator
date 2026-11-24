package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecyclerBoxTest {
    @Test
    void theClassicBoxIsGivenUnlessTheHotelAsksForTheMysteryBox() {
        assertEquals("ecotron_box", RecyclerBox.chosen(null));
        assertEquals("ecotron_box", RecyclerBox.chosen(""));
        assertEquals("ecotron_box", RecyclerBox.chosen("ecotron_box"));
        assertEquals("ecotron_box", RecyclerBox.chosen("throne"));
        assertEquals("matic_box", RecyclerBox.chosen("matic_box"));
        assertEquals("matic_box", RecyclerBox.chosen(" Matic_Box "));
    }

    @Test
    void bothBoxesOpen() {
        assertTrue(RecyclerBox.isBox("ecotron_box"));
        assertTrue(RecyclerBox.isBox("matic_box"));
        assertFalse(RecyclerBox.isBox("throne"));
        assertFalse(RecyclerBox.isBox(null));
    }

    @Test
    void theMysteryBoxCarriesAnIsoDateTheClassicBoxItsOldOne() {
        LocalDate day = LocalDate.of(2026, 3, 7);

        assertEquals("2026-03-07", RecyclerBox.date("matic_box", day));
        assertEquals("7-3-2026", RecyclerBox.date("ecotron_box", day));
        assertEquals("7-3-2026", RecyclerBox.date(null, day));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theHandlersUseTheChosenBoxAndOpenEither() throws Exception {
        assertTrue(source("polaris/messages/incoming/catalog/recycler/OpenRecycleBoxEvent.java")
                .contains("RecyclerBox.isBox(item.getBaseItem().getName())"));
        assertTrue(source("polaris/hotel/items/ItemManager.java")
                .contains("RecyclerBox.date(ecotron.getName(), LocalDate.now())"));

        String catalog = source("polaris/hotel/catalog/CatalogManager.java");
        assertTrue(catalog.contains("RecyclerBox.chosen("));
        // A hotel without the chosen furni still gets the classic box.
        assertTrue(catalog.contains("chosen != null ? chosen : items.getItem(RecyclerBox.CLASSIC)"));
    }
}
