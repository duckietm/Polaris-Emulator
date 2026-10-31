package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The gift commands created the content first and picked a wrapper afterwards: no wrapper configured threw on
 * nextInt(0), a wrapper id that is no furni failed in createGift, the content stayed behind as an orphan, and in
 * :massgift one exception ended the loop for everyone after.
 */
class CommandGiftsTest {

    @Test
    void noWrapperIsPickedWhenTheHotelHasNone() {
        assertNull(CommandGifts.pickWrapper(Map.of(), mock(ItemManager.class), new Random(1)));
        assertNull(CommandGifts.pickWrapper(null, mock(ItemManager.class), new Random(1)));
    }

    @Test
    void aWrapperIdThatIsNoFurniIsSkipped() {
        ItemManager items = mock(ItemManager.class);
        Item wrapper = mock(Item.class);
        when(items.getItem(2)).thenReturn(wrapper);
        Map<Integer, Integer> furniture = new LinkedHashMap<>();
        furniture.put(1, 99);
        furniture.put(2, 2);

        for (int seed = 0; seed < 5; seed++) {
            assertSame(wrapper, CommandGifts.pickWrapper(furniture, items, new Random(seed)));
        }
    }

    @Test
    void theContentIsRemovedWhenTheGiftCannotBeMade() {
        ItemManager items = mock(ItemManager.class);
        HabboItem content = mock(HabboItem.class);
        when(content.getId()).thenReturn(10);
        when(items.createGiftContent(eq(0), any(Item.class), anyInt(), anyInt(), anyString()))
                .thenReturn(content);
        when(items.createGift(anyString(), any(Item.class), anyString(), anyInt(), anyInt()))
                .thenReturn(null);

        assertFalse(CommandGifts.send(items, mock(Item.class), mock(Item.class), "alice", "hi"));
        verify(items).deleteItem(content);
    }

    @Test
    void nothingIsCreatedWithoutAWrapper() {
        ItemManager items = mock(ItemManager.class);

        assertFalse(CommandGifts.send(items, mock(Item.class), null, "alice", "hi"));
        verify(items, never()).createGiftContent(anyInt(), any(Item.class), anyInt(), anyInt(), anyString());
    }

    @Test
    void aDeliveredGiftKeepsItsContent() {
        ItemManager items = mock(ItemManager.class);
        HabboItem content = mock(HabboItem.class);
        when(items.createGiftContent(eq(0), any(Item.class), anyInt(), anyInt(), anyString()))
                .thenReturn(content);
        when(items.createGift(anyString(), any(Item.class), anyString(), anyInt(), anyInt()))
                .thenReturn(mock(HabboItem.class));

        assertTrue(CommandGifts.send(items, mock(Item.class), mock(Item.class), "alice", "hi"));
        verify(items, never()).deleteItem(content);
    }

    @Test
    void theThreeGiftCommandsUseIt() throws Exception {
        for (String command : List.of("GiftCommand", "MassGiftCommand", "RoomGiftCommand")) {
            String source =
                    Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/" + command + ".java"));

            assertTrue(source.contains("CommandGifts.send("), command);
            assertFalse(source.contains("nextInt(giftFurniture.size())"), command);
        }
    }
}
