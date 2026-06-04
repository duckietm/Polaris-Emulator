package com.eu.habbo.habbohotel.items;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FurnitureTextProviderTest {

    private FurnitureTextProvider provider(boolean enabled, FurnidataEntry... entries) {
        FurnitureTextProvider p = new FurnitureTextProvider(enabled);
        p.reindex(List.of(entries));
        return p;
    }

    @Test
    void resolvesNameByClassname() {
        FurnitureTextProvider p = provider(true,
            new FurnidataEntry(1, "chair_norja", FurnitureType.FLOOR, "Norja Chair", "Sit"));
        assertEquals("Norja Chair", p.getName("chair_norja"));
    }

    @Test
    void matchesBaseClassnameIgnoringColourVariantAndCase() {
        FurnitureTextProvider p = provider(true,
            new FurnidataEntry(1, "chair_norja*2", FurnitureType.FLOOR, "Norja Chair", "Sit"));
        assertEquals("Norja Chair", p.getName("CHAIR_NORJA"));
    }

    @Test
    void returnsNullWhenClassnameMissing() {
        FurnitureTextProvider p = provider(true);
        assertNull(p.getName("unknown_thing"));
    }

    @Test
    void returnsNullWhenDisabled() {
        FurnitureTextProvider p = provider(false,
            new FurnidataEntry(1, "chair_norja", FurnitureType.FLOOR, "Norja Chair", "Sit"));
        assertNull(p.getName("chair_norja"));
    }

    @Test
    void sanitizesNameCapStripControlAndNeutralizesPercent() {
        String evil = "Bad\nName %limit% %user.name%".repeat(20);
        FurnitureTextProvider p = provider(true,
            new FurnidataEntry(1, "x", FurnitureType.FLOOR, evil, ""));
        String name = p.getName("x");
        assertEquals(256, name.length(), "input far exceeds the cap, so it must be exactly 256");
        assertFalse(name.chars().anyMatch(Character::isISOControl), "no control chars remain after sanitize");
        assertFalse(name.contains("%"), "ASCII percent neutralized");
    }

    @Test
    void nullProviderNameNeverThrows() {
        FurnitureTextProvider p = provider(true);
        assertDoesNotThrow(() -> p.getName(null));
        assertNull(p.getName(null));
    }
}
