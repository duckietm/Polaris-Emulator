package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSaveRefusedException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredSelectorNamesAndAreaTest {
    @Test
    void namesAreStoredLowerCaseByTabWithoutTrimming() {
        assertEquals("robbie\tanna", WiredEffectUsersByName.normalizeNamesText("Robbie\tAnna\t\t"));
        // Rows saved with line breaks read as tabs.
        assertEquals("robbie\tanna", WiredEffectUsersByName.normalizeNamesText("Robbie\nAnna"));
        assertEquals(Set.of(" robbie", "anna"), WiredEffectUsersByName.parseUsernames(" robbie\t\tanna"));
    }

    @Test
    void aLongNameAndMoreThanTwentyNamesAreRefused() {
        String longName = "a".repeat(31);
        WiredSaveRefusedException length = assertThrows(
                WiredSaveRefusedException.class, () -> WiredEffectUsersByName.refuseOversizedNames(longName, longName));
        assertEquals("wiredfurni.error.username_length", length.getMessage());

        // 20 names with one empty between two tabs count 21.
        String names = "a\t".repeat(19) + "\tb";
        WiredSaveRefusedException count = assertThrows(
                WiredSaveRefusedException.class, () -> WiredEffectUsersByName.refuseOversizedNames(names, names));
        assertEquals("wiredfurni.error.usernames_count", count.getMessage());

        String twenty = "a\t".repeat(19) + "b";
        WiredEffectUsersByName.refuseOversizedNames(twenty, twenty);
    }

    @Test
    void areaValuesAreClampedToZeroAndSixtyFourAndMissingOnesAreZero() {
        int[] params = {-5, 3, 500};

        assertEquals(0, WiredSelectorPayloadGuard.areaValue(params, 0));
        assertEquals(3, WiredSelectorPayloadGuard.areaValue(params, 1));
        assertEquals(64, WiredSelectorPayloadGuard.areaValue(params, 2));
        assertEquals(0, WiredSelectorPayloadGuard.areaValue(params, 3));
    }
}
