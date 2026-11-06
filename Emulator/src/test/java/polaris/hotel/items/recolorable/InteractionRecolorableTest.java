package polaris.hotel.items.recolorable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemInteraction;
import polaris.hotel.items.interactions.InteractionRecolorable;

/** A recolourable furni keeps its colour next to its state without the rest of the emulator noticing. */
class InteractionRecolorableTest {

    private static InteractionRecolorable item(String extradata) {
        return new InteractionRecolorable(1, 1, null, extradata, 0, 0);
    }

    @Test
    void storedStateAndColourAreSplit() {
        InteractionRecolorable item = item("1|255,0,10");

        assertEquals("1", item.getExtradata());
        assertEquals(0xFF000A, item.getColor());
        assertEquals("1|255,0,10", item.getDatabaseExtraData());
    }

    @Test
    void aPlacementColourStartsInStateZero() {
        InteractionRecolorable item = item("12,34,56");

        assertEquals("0", item.getExtradata());
        assertEquals(0x0C2238, item.getColor());
    }

    @Test
    void changingTheStateKeepsTheColour() {
        InteractionRecolorable item = item("0|1,2,3");

        item.setExtradata("1");

        assertEquals("1", item.getExtradata());
        assertEquals("1|1,2,3", item.getDatabaseExtraData());
    }

    @Test
    void aFurniWithoutColourStoresOnlyItsState() {
        InteractionRecolorable item = item("0");

        assertFalse(item.hasColor());
        assertEquals("0", item.getDatabaseExtraData());

        item.setColor(0x123456);
        assertTrue(item.hasColor());
        assertEquals("0|18,52,86", item.getDatabaseExtraData());
    }

    // bc_cylinder_recolorable has the same five height states as the multiheight bc_cylinder.
    @Test
    void aRecolorableWithMultiHeightsStandsAtTheHeightOfItsState() {
        Item base = mock(Item.class);
        when(base.getMultiHeights()).thenReturn(new double[] {0.5, 1.0, 1.5});
        when(base.getHeight()).thenReturn(0.5);

        InteractionRecolorable item = new InteractionRecolorable(1, 1, base, "2|255,0,0", 0, 0);

        assertTrue(item.hasMultiHeights());
        assertEquals(1.5, Item.getCurrentHeight(item));
        assertEquals(0xFF0000, item.getColor());

        item.setExtradata("1");
        assertEquals(1.0, Item.getCurrentHeight(item));
    }

    @Test
    void aRecolorableWithoutMultiHeightsKeepsItsStackHeight() {
        Item base = mock(Item.class);
        when(base.getMultiHeights()).thenReturn(new double[0]);
        when(base.getHeight()).thenReturn(0.75);

        InteractionRecolorable item = new InteractionRecolorable(1, 1, base, "2", 0, 0);

        assertFalse(item.hasMultiHeights());
        assertEquals(0.75, Item.getCurrentHeight(item));
    }

    private static Item recolorableBase() {
        Item base = mock(Item.class);
        when(base.getName()).thenReturn("bc_quartercircle_recolorable");
        when(base.getInteractionType()).thenReturn(new ItemInteraction("recolorable", InteractionRecolorable.class));
        return base;
    }

    @Test
    void aNewFurniOnlyTakesAColourOfItsPalette() {
        Item base = recolorableBase();

        assertEquals("174,240,216", InteractionRecolorable.placementExtradata(base, "174,240,216", "0"));
        assertEquals(
                "255,255,255", InteractionRecolorable.placementExtradata(base, "1,2,3", "0"), "not in the palette");
        assertEquals("255,255,255", InteractionRecolorable.placementExtradata(base, "999,0,0", "0"));
        assertEquals("255,255,255", InteractionRecolorable.placementExtradata(base, "<script>", "0"));
        assertEquals("255,255,255", InteractionRecolorable.placementExtradata(base, null, "0"));
    }

    @Test
    void aStoredStateAndColourAreKeptWhenValid() {
        Item base = recolorableBase();

        assertEquals("2|174,240,216", InteractionRecolorable.placementExtradata(base, "2|174,240,216", "0"));
        assertEquals("255,255,255", InteractionRecolorable.placementExtradata(base, "x|1,2,3", "0"));
    }
}
