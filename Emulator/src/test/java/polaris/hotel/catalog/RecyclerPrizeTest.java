package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;

class RecyclerPrizeTest {
    private static Item item(int id) {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    @Test
    void aBoxHoldsAFurniByItsIdAndAChatStyleNegated() {
        assertEquals(77, RecyclerPrize.furni(item(77)).boxReward());
        assertEquals(-12, RecyclerPrize.chatStyle(12).boxReward());
        assertEquals(0, RecyclerPrize.chatStyle(0).boxReward());

        assertTrue(RecyclerPrize.chatStyle(12).isChatStyle());
        assertFalse(RecyclerPrize.furni(item(77)).isChatStyle());
    }

    @Test
    void anOldBoxWithAPositiveNumberIsAFurniBox() {
        assertEquals(0, RecyclerPrize.chatStyleOf(77));
        assertEquals(0, RecyclerPrize.chatStyleOf(0));
        assertEquals(12, RecyclerPrize.chatStyleOf(-12));
    }

    @Test
    void oneOfTheCandidatesIsPicked() {
        RecyclerPrize furni = RecyclerPrize.furni(item(1));
        RecyclerPrize style = RecyclerPrize.chatStyle(5);

        assertSame(furni, RecyclerPrize.pick(List.of(furni, style), bound -> 0));
        assertSame(style, RecyclerPrize.pick(List.of(furni, style), bound -> bound - 1));
        assertNull(RecyclerPrize.pick(List.of(), bound -> 0));
        assertNull(RecyclerPrize.pick(null, bound -> 0));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void aStyleTheUserOwnsIsNeverDrawnAndAStyleBoxGivesNoFurni() throws Exception {
        String catalog = source("polaris/hotel/catalog/CatalogManager.java");
        assertTrue(catalog.contains(".filter(style -> !owned.contains(style))"));

        String opening = source("polaris/hotel/items/RecycleBoxOpening.java");
        int style = opening.indexOf("int chatStyleId = RecyclerPrize.chatStyleOf(prize);");
        // The prize row is claimed first, and a chat style box returns before a furni would be inserted.
        assertTrue(opening.indexOf("DELETE_PRIZE)") < style);
        assertTrue(style < opening.indexOf("insertReward(connection, items, userId, prize)"));

        String handler = source("polaris/messages/incoming/catalog/recycler/OpenRecycleBoxEvent.java");
        assertTrue(handler.contains("ChatStyleRepository.grant(this.client.getUser(), chatStyleId);"));
    }
}
