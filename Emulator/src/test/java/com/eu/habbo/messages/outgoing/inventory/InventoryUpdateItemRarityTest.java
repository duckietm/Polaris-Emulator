package com.eu.habbo.messages.outgoing.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionMonsterPlantSeed;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * AIR {@code MapStuffData.rarityLevel}: a monster plant seed carries its rarity as the "rarity" map entry, which the
 * rarity plaque of the inventory grid, the infostand and the furni chooser read. An updated inventory item keeps it.
 */
class InventoryUpdateItemRarityTest {

    private static Item seedBase(String name) {
        Item item = mock(Item.class);
        when(item.getName()).thenReturn(name);
        when(item.getType()).thenReturn(FurnitureType.FLOOR);
        when(item.getSpriteId()).thenReturn(4582);
        return item;
    }

    @Test
    void aSeedRollsItsRarityOnceAndKeepsIt() {
        InteractionMonsterPlantSeed seed = new InteractionMonsterPlantSeed(1, 2, seedBase("mnstr_seed"), "", 0, 0);
        int level = seed.getRarityLevel();
        assertTrue(level >= 0 && level <= 9, "rolled " + level);
        assertEquals(
                level,
                new InteractionMonsterPlantSeed(1, 2, seedBase("mnstr_seed"), seed.getExtradata(), 0, 0)
                        .getRarityLevel());

        InteractionMonsterPlantSeed rare =
                new InteractionMonsterPlantSeed(3, 2, seedBase("mnstr_seed_rare"), "0", 0, 0);
        assertTrue(rare.getRarityLevel() >= 7, "a rare seed rolls the golden table");

        assertEquals(
                6,
                new InteractionMonsterPlantSeed(
                                4, 2, seedBase("mnstr_seed"), InteractionMonsterPlantSeed.extradataFor(6), 0, 0)
                        .getRarityLevel());
    }

    @Test
    void theInventoryUpdateCarriesTheRarityMap() {
        InteractionMonsterPlantSeed seed = new InteractionMonsterPlantSeed(
                11, 2, seedBase("mnstr_seed"), InteractionMonsterPlantSeed.extradataFor(4), 0, 0);

        ByteBuf packet = new InventoryUpdateItemComposer(seed).compose().get();
        packet.skipBytes(Integer.BYTES);
        assertEquals(Outgoing.InventoryItemUpdateComposer, packet.readUnsignedShort());
        packet.readInt();
        assertEquals("S", readString(packet));
        packet.readInt();
        assertEquals(4582, packet.readInt());
        assertEquals(1, packet.readInt(), "category");
        assertEquals(1, packet.readInt(), "MapStuffData format");
        assertEquals(1, packet.readInt());
        assertEquals("rarity", readString(packet));
        assertEquals("4", readString(packet));
    }

    @Test
    void aPaintItemKeepsItsCategoryAndLegacyString() {
        Item wallpaper = seedBase("wallpaper");
        when(wallpaper.getType()).thenReturn(FurnitureType.WALL);
        com.eu.habbo.habbohotel.users.HabboItem item =
                new com.eu.habbo.habbohotel.items.interactions.InteractionDefault(12, 2, wallpaper, "101", 0, 0);

        ByteBuf packet = new InventoryUpdateItemComposer(item).compose().get();
        packet.skipBytes(Integer.BYTES + Short.BYTES);
        packet.readInt();
        readString(packet);
        packet.readInt();
        packet.readInt();
        assertEquals(2, packet.readInt(), "wallpaper category, once");
        assertEquals(0, packet.readInt(), "legacy format");
        assertEquals("101", readString(packet));
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
}
