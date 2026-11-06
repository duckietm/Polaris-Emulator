package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import polaris.hotel.wired.core.WiredSourceUtil;

class WiredConditionFurniSelectionPayloadGuardTest {
    @Test
    void furniHaveFurniBoundsFurniSources() {
        WiredConditionFurniHaveFurni condition = new WiredConditionFurniHaveFurni(1, 1, null, "", 0, 0);

        assertEquals(WiredSourceUtil.SOURCE_SELECTED, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTOR));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeFurniSource(-10));
    }

    @Test
    void furniHaveHabboBoundsFurniSources() {
        WiredConditionFurniHaveUser condition = new WiredConditionFurniHaveUser(1, 1, null, "", 0, 0);

        assertEquals(WiredSourceUtil.SOURCE_SIGNAL, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SIGNAL));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeFurniSource(8_000));
    }

    @Test
    void theOfficialLayoutKeepsTheSourcesInTheSlotsAndOnlyOfficialParamsFirst() {
        polaris.core.ConfigurationManager config = org.mockito.Mockito.mock(polaris.core.ConfigurationManager.class);
        org.mockito.Mockito.when(config.getInt(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(20);
        try (org.mockito.MockedStatic<polaris.Emulator> emulator =
                org.mockito.Mockito.mockStatic(polaris.Emulator.class)) {
            emulator.when(polaris.Emulator::getConfig).thenReturn(config);

            WiredConditionFurniHaveUser avatars = new WiredConditionFurniHaveUser(1, 1, null, "", 0, 0);
            polaris.hotel.items.interactions.wired.WiredSettings all =
                    new polaris.hotel.items.interactions.wired.WiredSettings(new int[] {1}, "", new int[0], -1);
            all.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTOR});
            avatars.saveData(all);
            String saved = avatars.getWiredData();
            org.junit.jupiter.api.Assertions.assertTrue(saved.contains("\"furniSource\":200"), saved);
            org.junit.jupiter.api.Assertions.assertTrue(saved.contains("\"all\":true"), saved);

            WiredConditionTriggerOnFurni onFurni = new WiredConditionTriggerOnFurni(1, 1, null, "", 0, 0);
            polaris.hotel.items.interactions.wired.WiredSettings any =
                    new polaris.hotel.items.interactions.wired.WiredSettings(new int[0], "", new int[0], -1);
            any.setQuantifierCode(1);
            any.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SIGNAL});
            any.setUserSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTOR});
            onFurni.saveData(any);
            saved = onFurni.getWiredData();
            org.junit.jupiter.api.Assertions.assertTrue(saved.contains("\"furniSource\":201"), saved);
            org.junit.jupiter.api.Assertions.assertTrue(saved.contains("\"userSource\":200"), saved);
            org.junit.jupiter.api.Assertions.assertTrue(saved.contains("\"quantifier\":1"), saved);
        }
    }

    @Test
    void triggerOnFurniBoundsFurniUserSourcesAndQuantifier() {
        WiredConditionTriggerOnFurni condition = new WiredConditionTriggerOnFurni(1, 1, null, "", 0, 0);

        assertEquals(WiredSourceUtil.SOURCE_SELECTED, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeFurniSource(77));
        assertEquals(
                WiredSourceUtil.SOURCE_CLICKED_USER,
                condition.normalizeUserSource(WiredSourceUtil.SOURCE_CLICKED_USER));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeUserSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals(1, condition.normalizeQuantifier(1));
        assertEquals(0, condition.normalizeQuantifier(2));
    }
}
