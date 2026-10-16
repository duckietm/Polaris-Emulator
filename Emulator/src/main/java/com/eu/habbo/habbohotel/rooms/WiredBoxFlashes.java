package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The wired boxes whose state flipped since the room's last cycle. Habbo sends them together, one
 * ObjectsDataUpdate per 500 ms room cycle, and a box flips at most once in that window: a box that
 * fires ten times between two cycles still flashes once.
 */
public final class WiredBoxFlashes {
    private final Set<HabboItem> pending = new LinkedHashSet<>();

    WiredBoxFlashes() {}

    /**
     * Flips the box to its next flash state, unless it already flipped in this window.
     *
     * @return whether the box flipped
     */
    public synchronized boolean flip(HabboItem box, UnaryOperator<String> nextState) {
        if (box == null || !this.pending.add(box)) {
            return false;
        }

        box.setExtradata(nextState.apply(box.getExtradata()));
        return true;
    }

    /** Takes the boxes flipped since the last call, in the order they flipped. */
    public synchronized Set<HabboItem> drain() {
        if (this.pending.isEmpty()) {
            return Collections.emptySet();
        }

        Set<HabboItem> flipped = new LinkedHashSet<>(this.pending);
        this.pending.clear();
        return flipped;
    }

    public synchronized boolean isPending(HabboItem box) {
        return this.pending.contains(box);
    }

    /** The box state a normal wired box flips to: on and off. */
    public static String toggle(String current) {
        return "1".equals(current) ? "0" : "1";
    }

    /**
     * The state a selector flips to. It rests at 0, or at 3 when it filters the existing selection,
     * and then alternates between rest + 1 and rest + 2 each time its stack runs it.
     */
    public static String selector(String current, boolean filterMode) {
        int rest = filterMode ? 3 : 0;
        return String.valueOf(String.valueOf(rest + 1).equals(current) ? rest + 2 : rest + 1);
    }
}
