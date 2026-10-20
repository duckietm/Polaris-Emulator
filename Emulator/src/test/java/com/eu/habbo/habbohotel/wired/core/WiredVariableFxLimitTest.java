package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableFx;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** The variable fx boxes a room draws: past the cap the rest are left out and the monitor counts VARIABLE_FX_LIMIT. */
class WiredVariableFxLimitTest {

    @AfterEach
    void resetTheCap() {
        WiredVariableFxLimit.configure(WiredVariableFxLimit.DEFAULT_MAX_BOXES);
    }

    @Test
    void theCapIsTwentyFiveAndNegativeCountsAsZero() {
        assertEquals(25, WiredVariableFxLimit.DEFAULT_MAX_BOXES);
        assertEquals(25, WiredVariableFxLimit.maxBoxes());

        WiredVariableFxLimit.configure(-2);
        assertEquals(0, WiredVariableFxLimit.maxBoxes());
    }

    @Test
    void eachBoxOverTheCapIsCountedOnceNotOnEveryFlush() {
        WiredVariableFxLimit.configure(2);
        List<InteractionWiredExtra> extras = new ArrayList<>(List.of(fx(1), mock(WiredExtraUserVariable.class), fx(2)));

        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            assertEquals(0, WiredVariableFxLimit.noteLeftOut(5, extras, 0), "at the cap all are drawn");

            extras.add(fx(3));
            int leftOut = WiredVariableFxLimit.noteLeftOut(5, extras, 0);
            assertEquals(1, leftOut);
            assertEquals(1, WiredVariableFxLimit.noteLeftOut(5, extras, leftOut), "the next flush counts nothing");
            wired.verify(
                    () -> WiredManager.noteVariableFxLimit(
                            eq(5), contains("3 variable fx boxes; only 2"), eq("variable_fx"), eq(3)),
                    times(1));

            extras.add(fx(4));
            assertEquals(2, WiredVariableFxLimit.noteLeftOut(5, extras, leftOut));
            wired.verify(() -> WiredManager.noteVariableFxLimit(eq(5), anyString(), anyString(), anyInt()), times(2));
        }
    }

    @Test
    void withoutFxBoxesNothingIsCounted() {
        try (MockedStatic<WiredManager> wired = mockStatic(WiredManager.class)) {
            assertEquals(0, WiredVariableFxLimit.noteLeftOut(5, null, 0));
            assertEquals(0, WiredVariableFxLimit.noteLeftOut(null, 0));
            wired.verify(() -> WiredManager.noteVariableFxLimit(anyInt(), anyString(), anyString(), anyInt()), never());
        }
    }

    private static WiredExtraVariableFx fx(int id) {
        WiredExtraVariableFx box = mock(WiredExtraVariableFx.class);
        when(box.getId()).thenReturn(id);
        return box;
    }
}
