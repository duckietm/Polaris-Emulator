package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * "Use the triggering item" is the default furni source on every box that has one, and twenty of the
 * thirty-four events carry no furni of their own. Without a fallback the option answered with nothing
 * and the effect quietly did nothing - which is what "move furni to furni" on a says trigger did.
 */
class WiredSourceTriggerFallbackTest {

    private static WiredContext contextWith(HabboItem sourceItem, HabboItem triggerItem) {
        WiredContext ctx = mock(WiredContext.class);
        Room room = mock(Room.class);
        when(ctx.room()).thenReturn(room);
        when(ctx.sourceItem()).thenReturn(Optional.ofNullable(sourceItem));
        when(ctx.triggerItem()).thenReturn(triggerItem);
        return ctx;
    }

    @Test
    @DisplayName("an event that carries a furni still resolves to that furni")
    void theEventsOwnFurniWins() {
        HabboItem walkedOn = mock(HabboItem.class);
        HabboItem wiredBox = mock(HabboItem.class);

        List<HabboItem> resolved =
                WiredSourceUtil.resolveItemsRaw(contextWith(walkedOn, wiredBox), WiredSourceUtil.SOURCE_TRIGGER, null);

        assertEquals(1, resolved.size());
        assertSame(walkedOn, resolved.get(0), "the walked-on furni, not the wired box");
    }

    @Test
    @DisplayName("an event with no furni falls back to the wired trigger box")
    void theWiredBoxStandsInForEventsWithNoFurni() {
        HabboItem wiredBox = mock(HabboItem.class);

        List<HabboItem> resolved =
                WiredSourceUtil.resolveItemsRaw(contextWith(null, wiredBox), WiredSourceUtil.SOURCE_TRIGGER, null);

        assertEquals(1, resolved.size());
        assertSame(wiredBox, resolved.get(0));
    }

    @Test
    @DisplayName("with neither, the answer is still nothing rather than a null in the list")
    void nothingToStandInFor() {
        assertTrue(WiredSourceUtil.resolveItemsRaw(contextWith(null, null), WiredSourceUtil.SOURCE_TRIGGER, null)
                .isEmpty());
    }

    @Test
    @DisplayName("a null context resolves to nothing whatever the source")
    void aMissingContextResolvesToNothing() {
        for (int source : new int[] {
            WiredSourceUtil.SOURCE_TRIGGER,
            WiredSourceUtil.SOURCE_SELECTED,
            WiredSourceUtil.SOURCE_SELECTOR,
            WiredSourceUtil.SOURCE_SIGNAL
        }) {
            assertTrue(WiredSourceUtil.resolveItemsRaw(null, source, null).isEmpty(), "source " + source);
        }
    }

    @Test
    @DisplayName("picked furni are returned as picked, and never stood in for")
    void pickedFurniAreLeftAlone() {
        HabboItem picked = mock(HabboItem.class);
        HabboItem wiredBox = mock(HabboItem.class);

        List<HabboItem> resolved = WiredSourceUtil.resolveItemsRaw(
                contextWith(null, wiredBox), WiredSourceUtil.SOURCE_SELECTED, List.of(picked));

        assertEquals(1, resolved.size());
        assertSame(picked, resolved.get(0));
    }
}
