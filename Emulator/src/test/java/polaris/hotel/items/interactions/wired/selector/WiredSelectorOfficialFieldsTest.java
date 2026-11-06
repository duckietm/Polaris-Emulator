package polaris.hotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.wired.core.WiredSourceUtil;

/** Selectors take filter / invert from the official fields and their sources from the official slots. */
class WiredSelectorOfficialFieldsTest {

    private static WiredSettings settings(int[] params, boolean filter, boolean invert) {
        WiredSettings settings = new WiredSettings(params, "", new int[0], 0);
        settings.setSelectorFilter(filter);
        settings.setSelectorInvert(invert);
        return settings;
    }

    @Test
    void filterAndInvertComeFromTheOfficialFields() throws Exception {
        WiredEffectUsersSignal signal = new WiredEffectUsersSignal(1, 1, null, "", 0, 0);
        signal.saveData(settings(new int[0], true, false), null);
        assertTrue(signal.selectorFilter());
        assertFalse(signal.selectorInvert());

        WiredEffectUsersTeam team = new WiredEffectUsersTeam(1, 1, null, "", 0, 0);
        // [team]; a stale filter/invert pair in the params is ignored
        team.saveData(settings(new int[] {2, 1, 1}, false, true), null);
        assertFalse(team.selectorFilter());
        assertTrue(team.selectorInvert());
    }

    @Test
    void usersOnFurniTakesItsFurniFromTheSlot() throws Exception {
        WiredEffectUsersOnFurni onFurni = new WiredEffectUsersOnFurni(1, 1, null, "", 0, 0);
        WiredSettings settings = settings(new int[0], false, false);
        settings.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTOR});
        polaris.core.ConfigurationManager config = org.mockito.Mockito.mock(polaris.core.ConfigurationManager.class);
        org.mockito.Mockito.when(config.getInt(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(20);
        try (org.mockito.MockedStatic<polaris.Emulator> emulator =
                org.mockito.Mockito.mockStatic(polaris.Emulator.class)) {
            emulator.when(polaris.Emulator::getConfig).thenReturn(config);
            onFurni.saveData(settings, null);
        }
        assertArrayEquals(new int[] {WiredSourceUtil.SOURCE_SELECTOR}, onFurni.officialFurniSources());
    }
}
