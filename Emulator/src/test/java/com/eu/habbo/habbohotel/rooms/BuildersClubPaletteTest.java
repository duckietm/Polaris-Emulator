package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The ~recolorable_furni.color.* variables: colour parts, the nearest palette colour and the room budget. */
class BuildersClubPaletteTest {

    @Test
    void theColourAndItsPartsAreRead() {
        int pink = 0xFFB7BC;
        assertEquals(0xFFB7BC, BuildersClubPalette.component(pink, BuildersClubPalette.RGB));
        assertEquals(255, BuildersClubPalette.component(pink, BuildersClubPalette.RED));
        assertEquals(183, BuildersClubPalette.component(pink, BuildersClubPalette.GREEN));
        assertEquals(188, BuildersClubPalette.component(pink, BuildersClubPalette.BLUE));
    }

    @Test
    void aWrittenPartReplacesOnlyThatPartAndValuesAreClamped() {
        assertEquals(0x10B7BC, BuildersClubPalette.withComponent(0xFFB7BC, BuildersClubPalette.RED, 0x10));
        assertEquals(0xFF00BC, BuildersClubPalette.withComponent(0xFFB7BC, BuildersClubPalette.GREEN, -5));
        assertEquals(0xFFB7FF, BuildersClubPalette.withComponent(0xFFB7BC, BuildersClubPalette.BLUE, 999));
        assertEquals(0x123456, BuildersClubPalette.withComponent(0, BuildersClubPalette.RGB, 0x123456));
        assertEquals(0xFFFFFF, BuildersClubPalette.withComponent(0, BuildersClubPalette.RGB, Integer.MAX_VALUE));
    }

    @Test
    void theSwatchIsTheLastPartColourThatIsNotWhiteOfTheVariantItself() {
        String red = "{\"classname\":\"bc_block*3\",\"partcolors\":{\"color\":[\"#FFFFFF\",\"#AA0000\",\"#ffffff\"]}}";
        assertEquals(0xAA0000, BuildersClubPalette.swatchOf(red, "bc_block*3"));
        assertEquals(
                0xFFFFFF,
                BuildersClubPalette.swatchOf(
                        "{\"classname\":\"bc_block*1\",\"partcolors\":{\"color\":[\"#FFFFFF\"]}}", "bc_block*1"));
        assertEquals(BuildersClubPalette.NO_COLOR, BuildersClubPalette.swatchOf(red, "bc_block*4"));
        assertEquals(BuildersClubPalette.NO_COLOR, BuildersClubPalette.swatchOf("{}", "bc_block*3"));
        assertEquals(BuildersClubPalette.NO_COLOR, BuildersClubPalette.swatchOf("not json", "bc_block*3"));
    }

    @Test
    void theNearestPaletteColourWinsAndOnATieTheCurrentStays() {
        Item red = block(1);
        Item darkRed = block(2);
        Item green = block(3);
        Item otherShape = block(4);
        when(otherShape.getWidth()).thenReturn(2);
        Map<Item, Integer> colours = Map.of(red, 0xFF0000, darkRed, 0x800000, green, 0x00FF00, otherShape, 0x00FE00);
        List<Item> palette = List.of(red, darkRed, green, otherShape);

        assertSame(green, BuildersClubPalette.nearest(red, 0x10F010, palette, colours::get));
        assertSame(darkRed, BuildersClubPalette.nearest(red, 0x700000, palette, colours::get));
        assertSame(red, BuildersClubPalette.nearest(red, 0xFF0000, palette, colours::get));
        Item twinOfRed = block(5);
        Map<Item, Integer> tie = Map.of(red, 0xFF0000, twinOfRed, 0xFF0000);
        assertSame(red, BuildersClubPalette.nearest(red, 0xFF0000, List.of(twinOfRed, red), tie::get));
    }

    @Test
    void aRoomGetsALimitedNumberOfRecolorsPerSecond() {
        int roomId = 990_501;
        long now = 70_000L;
        for (int i = 0; i < BuildersClubPalette.MAX_RECOLORS_PER_SECOND; i++) {
            assertTrue(BuildersClubPalette.spend(roomId, now));
        }
        assertFalse(BuildersClubPalette.spend(roomId, now + 999));
        assertTrue(BuildersClubPalette.spend(roomId, now + 1_000));
    }

    @Test
    void theKeysAreKnown() {
        assertTrue(BuildersClubPalette.isKey(BuildersClubPalette.RGB));
        assertTrue(BuildersClubPalette.isKey(BuildersClubPalette.BLUE));
        assertFalse(BuildersClubPalette.isKey("@state"));
    }

    private static Item block(int id) {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(id);
        when(item.getType()).thenReturn(FurnitureType.FLOOR);
        when(item.getWidth()).thenReturn(1);
        when(item.getLength()).thenReturn(1);
        when(item.getHeight()).thenReturn(1.0);
        when(item.allowStack()).thenReturn(true);
        when(item.getStateCount()).thenReturn(1);
        when(item.getInteractionType()).thenReturn(new ItemInteraction("default", InteractionDefault.class));
        return item;
    }
}
