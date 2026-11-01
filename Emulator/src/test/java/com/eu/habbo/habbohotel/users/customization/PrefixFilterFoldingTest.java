package com.eu.habbo.habbohotel.users.customization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A prefix with a blocked word written with an accent or a zero-width character passed the filter, which compared the
 * raw text, and showed next to the name for the whole room.
 */
class PrefixFilterFoldingTest {

    @Test
    void accentsCaseAndInvisibleCharactersFoldAway() {
        assertEquals("badword", UserCustomizationPurchaseService.foldForFilter("BÄDWÖRD"));
        assertEquals("badword", UserCustomizationPurchaseService.foldForFilter("bad​word"));
    }

    @Test
    void formatCharactersAndMarkupAreRefusedInAPrefix() {
        assertTrue(UserCustomizationPurchaseService.containsControlChars("bad​word"));
        assertTrue(UserCustomizationPurchaseService.containsControlChars("<b>x</b>"));
        assertFalse(UserCustomizationPurchaseService.containsControlChars("Él Capitán"));
    }
}
