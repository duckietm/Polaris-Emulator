package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.ItemsDataUpdateComposer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

/**
 * The wired box flashes of a room. As in Habbo, a flash goes out in the pass that fired it, in the same packet batch
 * as the chat or click that fired the stack: with the firing's furni states (ahead of its movements), or at the end
 * of the dispatch. A box flips at most once per 500 ms: a box that fires ten times in that window flashes once, and
 * two stacks fired 200 ms apart flash 200 ms apart.
 *
 * <p>Flips made outside any firing pass (or left over) go out with the next room cycle. Behaviour observed in
 * plus-emulator/PlusEMU f600db7d and 8d90382c, reimplemented.
 */
public final class WiredBoxFlashes {
    /** How long a box waits after a flip before it may flip again. */
    public static final long FLIP_WINDOW_MS = 500L;

    private static final ThreadLocal<Deque<Boolean>> PASS_DEPTH = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Set<WiredBoxFlashes>> DIRTY = ThreadLocal.withInitial(LinkedHashSet::new);

    private final Room room;
    private final LongSupplier clock;
    private final Set<HabboItem> pending = new LinkedHashSet<>();
    private final Map<HabboItem, Long> lastFlipAt = new LinkedHashMap<>();

    WiredBoxFlashes() {
        this(null, System::currentTimeMillis);
    }

    WiredBoxFlashes(Room room) {
        this(room, System::currentTimeMillis);
    }

    WiredBoxFlashes(Room room, LongSupplier clock) {
        this.room = room;
        this.clock = clock;
    }

    /**
     * Flips the box to its next flash state, unless it flipped less than {@link #FLIP_WINDOW_MS} ago.
     *
     * @return whether the box flipped
     */
    public boolean flip(HabboItem box, UnaryOperator<String> nextState) {
        if (box == null) {
            return false;
        }

        synchronized (this) {
            long now = this.clock.getAsLong();
            Long last = this.lastFlipAt.get(box);
            if (last != null && now - last < FLIP_WINDOW_MS) {
                return false;
            }

            this.lastFlipAt.put(box, now);
            this.pending.add(box);
            box.setExtradata(nextState.apply(box.getExtradata()));
        }

        if (isPassOpen() || WiredFurniStateBatch.isOpen()) {
            DIRTY.get().add(this);
        }
        return true;
    }

    /** Takes the boxes flipped and not sent yet, in the order they flipped. */
    public synchronized Set<HabboItem> drain() {
        long now = this.clock.getAsLong();
        this.lastFlipAt.values().removeIf(at -> now - at >= FLIP_WINDOW_MS);

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

    Room room() {
        return this.room;
    }

    /** Whether flashes go out to the room's users (not while the room hides its wired). */
    boolean visible() {
        return this.room != null && !this.room.isHideWired();
    }

    /** Opens a firing pass on this thread (a wired dispatch); every open needs its {@link #closePass()}. */
    public static void openPass() {
        PASS_DEPTH.get().push(Boolean.TRUE);
    }

    /** Closes the pass; the outermost close sends the flashes the pass flipped and has not sent yet. */
    public static void closePass() {
        Deque<Boolean> depth = PASS_DEPTH.get();
        if (depth.isEmpty()) return;
        depth.pop();
        if (!depth.isEmpty() || WiredFurniStateBatch.isOpen()) return;

        flushDirty();
    }

    static boolean isPassOpen() {
        return !PASS_DEPTH.get().isEmpty();
    }

    /**
     * Takes, for one room, the flashes this thread flipped and has not sent yet. The furni-state batch adds them to
     * its own packet, after the furni states and ahead of the movements.
     */
    static Set<HabboItem> takeDirty(Room room) {
        Set<WiredBoxFlashes> dirty = DIRTY.get();
        for (Iterator<WiredBoxFlashes> it = dirty.iterator(); it.hasNext(); ) {
            WiredBoxFlashes flashes = it.next();
            if (flashes.room == room) {
                it.remove();
                return flashes.visible() ? flashes.drain() : Collections.emptySet();
            }
        }
        return Collections.emptySet();
    }

    /** Sends every flash this thread flipped and has not sent yet, one packet per room. */
    static void flushDirty() {
        Set<WiredBoxFlashes> dirty = DIRTY.get();
        if (dirty.isEmpty()) return;

        Set<WiredBoxFlashes> rooms = new LinkedHashSet<>(dirty);
        dirty.clear();
        for (WiredBoxFlashes flashes : rooms) {
            if (!flashes.visible()) {
                // Kept for the room cycle, which drops them the same way.
                continue;
            }
            Set<HabboItem> flipped = flashes.drain();
            if (!flipped.isEmpty()) {
                flashes.room.sendComposer(new ItemsDataUpdateComposer(flipped).compose());
            }
        }
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
