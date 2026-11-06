package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The furniture label keeps the official client's rules for its text and settings. */
class WiredExtraLabelTest {

    @Test
    void textKeepsLineBreaksAndDropsControlCharacters() {
        assertEquals("a\nb\nc d", WiredExtraLabel.normalizeText("a\r\nb\rc\td\u0007"));
        assertEquals("", WiredExtraLabel.normalizeText(null));
        assertEquals(
                WiredExtraLabel.MAX_TEXT_LENGTH,
                WiredExtraLabel.normalizeText("x".repeat(5000)).length());
    }

    @Test
    void onlyTheEightOfficialColoursAreKept() {
        assertTrue(WiredExtraLabel.isKnownColor(4278912));
        assertTrue(WiredExtraLabel.isKnownColor(5592405));
        assertFalse(WiredExtraLabel.isKnownColor(0xFFFFFF));
        assertEquals(8, WiredExtraLabel.COLORS.length);
    }

    @Test
    void settingsFollowTheOfficialWindow() {
        WiredExtraLabel label = new WiredExtraLabel(1, 1, null, "", 0, 0);

        label.setSettings(0xFFFFFF, 7, true);
        assertEquals(WiredExtraLabel.DEFAULT_COLOR, label.getColor());
        assertEquals(WiredExtraLabel.VISIBILITY_WIRED_INSPECTORS, label.getVisibility());
        assertFalse(label.isAlwaysOpen(), "only a label everyone may see can stay open");

        label.setSettings(6834048, WiredExtraLabel.VISIBILITY_EVERYONE, true);
        assertEquals(6834048, label.getColor());
        assertTrue(label.isAlwaysOpen());
        assertTrue(label.isVisibleTo(null, null), "everyone sees it, even without a room");
    }
}
