package com.eu.habbo.habbohotel.items.interactions.mysterybox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MysteryBoxColourTest {
    @Test
    void theColourIsTheLastWordWhenItIsOneTheClientCanDraw() {
        assertEquals("purple", MysteryBoxColour.of("mystery_box_purple"));
        assertEquals("turquoise", MysteryBoxColour.of("MYSTERY_KEY_TURQUOISE"));
        assertEquals("red", MysteryBoxColour.of("xmas_mystery_key_red"));
    }

    @Test
    void furnitureWithoutAKnownColourIsColourless() {
        assertEquals("", MysteryBoxColour.of("mystery_box"));
        assertEquals("", MysteryBoxColour.of("mystery_box_gold"));
        assertEquals("", MysteryBoxColour.of(""));
        assertEquals("", MysteryBoxColour.of(null));
    }

    @Test
    void twoColourlessPiecesMatchEachOther() {
        assertEquals(MysteryBoxColour.of("mystery_box"), MysteryBoxColour.of("mystery_key"));
    }
}
