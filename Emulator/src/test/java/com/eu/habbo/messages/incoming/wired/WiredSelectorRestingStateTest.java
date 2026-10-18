package com.eu.habbo.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.selector.WiredEffectFurniPicks;
import org.junit.jupiter.api.Test;

/** Every selector save goes back to its resting state: 3 with Filter, 0 without; Invert does not change it. */
class WiredSelectorRestingStateTest {

    private static WiredEffectFurniPicks selector(boolean filter, boolean invert) {
        WiredEffectFurniPicks box = new WiredEffectFurniPicks(1, 1, mock(Item.class), "", 0, 0);
        WiredSettings settings = new WiredSettings(new int[0], "", new int[0], 0);
        settings.setSelectorFilter(filter);
        settings.setSelectorInvert(invert);
        box.saveData(settings, null);
        box.setExtradata("1");
        return box;
    }

    @Test
    void restingStateFollowsFilterOnly() {
        assertEquals("0", WiredEffectSaveDataEvent.selectorRestingState(selector(false, false)));
        assertEquals("0", WiredEffectSaveDataEvent.selectorRestingState(selector(false, true)));
        assertEquals("3", WiredEffectSaveDataEvent.selectorRestingState(selector(true, false)));
        assertEquals("3", WiredEffectSaveDataEvent.selectorRestingState(selector(true, true)));
    }
}
