package com.eu.habbo.habbohotel.rooms;

/** Immutable, session-only effective visual state for one room furniture item. */
public record WiredOpacityState(int itemId, boolean wallItem, int opacity, boolean clickThrough) {
    public WiredOpacityState {
        opacity = Math.max(0, Math.min(100, opacity));
    }
}
