package com.eu.habbo.util.imager.badges;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.guilds.GuildPart;
import com.eu.habbo.habbohotel.guilds.GuildPartType;
import java.awt.image.BufferedImage;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuildBadgeComposerTest {
    private static BufferedImage solid(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    /** Base 1 is a white 39x39 square; symbol 2 a white 9x9 square with a black 3x3 overlay; colour 3 red. */
    private static final GuildBadgeComposer.Parts PARTS = new GuildBadgeComposer.Parts() {
        private final Map<String, BufferedImage> images = Map.of(
                "base_basic.gif", solid(39, 39, 0xFFFFFFFF),
                "symbol_star.gif", solid(9, 9, 0xFFFFFFFF),
                "symbol_star_overlay.gif", solid(3, 3, 0xFF000000));

        @Override
        public GuildPart part(GuildPartType type, int id) {
            if (type == GuildPartType.BASE && id == 1) {
                return new GuildPart(1, "base_basic.gif", "");
            }
            if (type == GuildPartType.SYMBOL && id == 2) {
                return new GuildPart(2, "symbol_star.gif", "symbol_star_overlay.gif");
            }
            if (type == GuildPartType.BASE_COLOR && id == 3) {
                return new GuildPart(3, "ff0000", "");
            }
            if (type == GuildPartType.BASE_COLOR && id == 4) {
                return new GuildPart(4, "0000ff", "");
            }
            return null;
        }

        @Override
        public BufferedImage image(String partValue) {
            return this.images.get(partValue);
        }
    };

    @Test
    void codesAreSevenCharacterParts() {
        assertTrue(GuildBadgeComposer.isBadgeCode("b001034s002044"));
        assertFalse(GuildBadgeComposer.isBadgeCode("b0103"));
        assertFalse(GuildBadgeComposer.isBadgeCode("b001034s00204x"));
        assertFalse(GuildBadgeComposer.isBadgeCode("b001034b001034b001034b001034b001034b001034"), "six parts");
        assertFalse(GuildBadgeComposer.isBadgeCode("../etc"));
        assertArrayEquals(new String[] {"b010340", "s020440"}, GuildBadgeComposer.split("b010340s020440"));
    }

    @Test
    void theBaseIsRecolouredAndTheSymbolSitsAtItsPosition() {
        // b + id 001 + colour 03 + position 4 (centre); s + id 002 + colour 04 + position 4.
        BufferedImage badge = GuildBadgeComposer.compose("b001034s002044", PARTS);

        assertNotNull(badge);
        assertEquals(39, badge.getWidth());
        assertEquals(0xFFFF0000, badge.getRGB(0, 0), "the base takes colour 03");
        // The 9x9 symbol is centred: (15..23); recoloured blue; its 3x3 overlay centred (18..20) stays black.
        assertEquals(0xFF0000FF, badge.getRGB(15, 15));
        assertEquals(0xFF000000, badge.getRGB(19, 19));
        assertEquals(0xFFFF0000, badge.getRGB(14, 14));
    }

    @Test
    void nothingToDrawGivesNull() {
        assertNull(GuildBadgeComposer.compose("b099034", PARTS), "unknown part");
        assertNull(GuildBadgeComposer.compose("not-a-code", PARTS));
        assertNull(GuildBadgeComposer.compose("b001034", null));
    }
}
