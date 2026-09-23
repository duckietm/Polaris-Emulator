package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredRoomLinker;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InteractionWiredRoomLinkerTest {

    private static Item base() {
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        return base;
    }

    @Test
    void theRoomLinkerInteractionNameResolvesToTheLinker() {
        TestItemManager manager = new TestItemManager();
        manager.loadDefaults();

        assertSame(
                InteractionWiredRoomLinker.class,
                manager.getItemInteraction("wf_room_linker").getType());
    }

    @Test
    void aLinkerIsATeleporterSoItIsBoughtAndResolvedAsAPair() {
        assertTrue(InteractionTeleport.class.isAssignableFrom(InteractionWiredRoomLinker.class));
    }

    @Test
    void clickingALinkerStartsNoTeleport() throws Exception {
        InteractionWiredRoomLinker linker = new InteractionWiredRoomLinker(7, 1, base(), "", 0, 0);
        Room room = mock(Room.class);
        GameClient client = mock(GameClient.class);

        linker.onClick(client, room, new Object[0]);

        verifyNoInteractions(room, client);
        assertTrue("0".equals(linker.getExtradata()), "a linker keeps no teleport state");
    }

    @Test
    void aLinkerCanBeStoodOnAndIsNotAUsableTeleporter() {
        InteractionWiredRoomLinker linker = new InteractionWiredRoomLinker(7, 1, base(), "", 0, 0);

        assertTrue(linker.canWalkOn(mock(RoomUnit.class), mock(Room.class), new Object[0]));
        assertTrue(linker.isWalkable());
        assertFalse(linker.isUsable());
        assertFalse(linker.invalidatesToRoomKick());
    }

    @Test
    void aGiftPurchasePairsEveryTeleporterKindIncludingTheLinker() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java"));

        assertTrue(source.contains("InteractionTeleport.class.isAssignableFrom("), "pairs by assignability");
        assertFalse(source.contains("== InteractionTeleport.class"), "no exact-class pairing left");
    }

    @Test
    void theRoomSendsALinkerAsAFurniNobodyUses() throws Exception {
        String added = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/outgoing/rooms/items/AddFloorItemComposer.java"));
        String listed = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/outgoing/rooms/items/RoomFloorItemsComposer.java"));

        // Teleporters are sent as usable by everyone; the linker falls through to isUsable(), which is false.
        assertTrue(added.contains(
                "(this.item instanceof InteractionTeleport && !(this.item instanceof InteractionWiredRoomLinker))"));
        assertTrue(listed.contains(
                "(item instanceof InteractionTeleport && !(item instanceof InteractionWiredRoomLinker))"));
    }

    private static final class TestItemManager extends ItemManager {
        private void loadDefaults() {
            loadItemInteractions();
        }
    }
}
