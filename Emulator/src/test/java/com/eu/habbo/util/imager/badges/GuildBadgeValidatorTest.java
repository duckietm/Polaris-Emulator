package com.eu.habbo.util.imager.badges;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.guilds.GuildPart;
import com.eu.habbo.habbohotel.guilds.GuildPartType;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class GuildBadgeValidatorTest {
    /** Base 1, symbol 2, a symbol 3 whose image name is unsafe, colours 3 and 4. */
    private static final GuildBadgeComposer.Parts PARTS = new GuildBadgeComposer.Parts() {
        @Override
        public GuildPart part(GuildPartType type, int id) {
            if (type == GuildPartType.BASE && id == 1) return new GuildPart(1, "base_basic.gif", "");
            if (type == GuildPartType.SYMBOL && id == 2) return new GuildPart(2, "symbol_star.gif", "");
            if (type == GuildPartType.SYMBOL && id == 3) return new GuildPart(3, "../evil", "");
            if (type == GuildPartType.BASE_COLOR && (id == 3 || id == 4)) return new GuildPart(id, "ff0000", "");
            return null;
        }

        @Override
        public BufferedImage image(String partValue) {
            return null;
        }
    };

    @Test
    void acceptsInstalledPartsAndEmptySymbolSlots() {
        assertTrue(GuildBadgeValidator.isValid("b001034", PARTS));
        assertTrue(GuildBadgeValidator.isValid("b001034s002044s000000", PARTS));
    }

    @Test
    void refusesUnknownPartsColoursAndOrder() {
        assertFalse(GuildBadgeValidator.isValid("b009034", PARTS), "unknown base");
        assertFalse(GuildBadgeValidator.isValid("b001034s009044", PARTS), "unknown symbol");
        assertFalse(GuildBadgeValidator.isValid("b001094", PARTS), "unknown colour");
        assertFalse(GuildBadgeValidator.isValid("s002044", PARTS), "no base first");
        assertFalse(GuildBadgeValidator.isValid("b001034b001034", PARTS), "second base");
        assertFalse(GuildBadgeValidator.isValid("b001034x002044", PARTS), "unknown type");
        assertFalse(GuildBadgeValidator.isValid("b001034s003044", PARTS), "unsafe part image name");
        assertFalse(GuildBadgeValidator.isValid("b001034/../x", PARTS), "not a code");
        assertFalse(GuildBadgeValidator.isValid(null, PARTS));
    }
}
