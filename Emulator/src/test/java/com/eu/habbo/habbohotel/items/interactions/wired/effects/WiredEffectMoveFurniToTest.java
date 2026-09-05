package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * This box moves the furni the event carried and places it beside the furni the dialog selected. It
 * used to read that first furni out of the legacy settings array, which the current engine builds as
 * null on every path - so the only effect that reads it iterated nothing, and the box did nothing at
 * all whatever it was wired to, with no error anywhere to say so.
 */
class WiredEffectMoveFurniToTest {

    @Test
    @DisplayName("with no legacy array it moves the furni the event carried")
    void fallsBackToTheEventSourceItem() {
        HabboItem carried = mock(HabboItem.class);
        WiredEvent event = mock(WiredEvent.class);
        when(event.getSourceItem()).thenReturn(Optional.of(carried));

        WiredContext ctx = mock(WiredContext.class);
        when(ctx.legacySettings()).thenReturn(new Object[0]);
        when(ctx.event()).thenReturn(event);

        Object[] moved = WiredEffectMoveFurniTo.triggeringFurni(ctx);

        assertEquals(1, moved.length, "the event's furni is the one that moves");
        assertSame(carried, moved[0]);
    }

    @Test
    @DisplayName("an event carrying no furni leaves nothing to move")
    void noSourceItemMovesNothing() {
        WiredEvent event = mock(WiredEvent.class);
        when(event.getSourceItem()).thenReturn(Optional.empty());

        WiredContext ctx = mock(WiredContext.class);
        when(ctx.legacySettings()).thenReturn(new Object[0]);
        when(ctx.event()).thenReturn(event);

        assertArrayEquals(new Object[0], WiredEffectMoveFurniTo.triggeringFurni(ctx));
    }

    @Test
    @DisplayName("a populated legacy array still wins, so plugins keep working")
    void legacyArrayStillWins() {
        HabboItem legacy = mock(HabboItem.class);
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.legacySettings()).thenReturn(new Object[] {legacy});

        Object[] moved = WiredEffectMoveFurniTo.triggeringFurni(ctx);

        assertEquals(1, moved.length);
        assertSame(legacy, moved[0]);
    }
}
