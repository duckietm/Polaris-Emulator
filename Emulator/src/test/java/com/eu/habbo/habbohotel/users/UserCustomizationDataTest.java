package com.eu.habbo.habbohotel.users;

import com.eu.habbo.habbohotel.users.inventory.UserVisualSettingsComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the normalization done by the UserCustomizationData record's compact
 * constructor (Phase 1 record conversion): null carrier fields become "", and the
 * display order is run through UserVisualSettingsComponent.sanitizeDisplayOrder.
 * Pure logic — no DB.
 */
class UserCustomizationDataTest {

    @Test
    void normalizesNullFieldsToEmptyAndDisplayOrderToDefault() {
        UserCustomizationData data = new UserCustomizationData(null, null, null, null, null, null, null);

        assertEquals("", data.nickIcon());
        assertEquals("", data.prefixText());
        assertEquals("", data.prefixColor());
        assertEquals("", data.prefixIcon());
        assertEquals("", data.prefixEffect());
        assertEquals("", data.prefixFont());
        assertEquals(UserVisualSettingsComponent.DEFAULT_DISPLAY_ORDER, data.displayOrder());
    }

    @Test
    void emptyFactoryProducesAllDefaults() {
        UserCustomizationData data = UserCustomizationData.empty();

        assertEquals("", data.nickIcon());
        assertEquals("", data.prefixText());
        assertEquals(UserVisualSettingsComponent.DEFAULT_DISPLAY_ORDER, data.displayOrder());
    }

    @Test
    void preservesValidValuesAndKeepsValidDisplayOrder() {
        UserCustomizationData data = new UserCustomizationData(
                "nick", "name-prefix-icon", "txt", "#fff", "ic", "fx", "font");

        assertEquals("nick", data.nickIcon());
        assertEquals("txt", data.prefixText());
        assertEquals("font", data.prefixFont());
        // a valid 3-part order of allowed unique parts passes through (lowercased)
        assertEquals("name-prefix-icon", data.displayOrder());
    }

    @Test
    void invalidDisplayOrderFallsBackToDefault() {
        UserCustomizationData data = new UserCustomizationData("", "bogus-order", "", "", "", "", "");

        assertEquals(UserVisualSettingsComponent.DEFAULT_DISPLAY_ORDER, data.displayOrder());
    }
}
