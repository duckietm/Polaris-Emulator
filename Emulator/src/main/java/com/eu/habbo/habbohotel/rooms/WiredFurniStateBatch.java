package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.ItemsDataUpdateComposer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * The floor furni states a wired firing writes (Toggle State, snapshot restores, antenna flips). Habbo sends them
 * together in one ObjectsDataUpdate per wired tick, ahead of that tick's movement packet, instead of one state packet
 * per furni. A furni written twice in the same firing goes out once, with its last state.
 *
 * <p>The batch belongs to the thread running the firing and nests: only the outermost {@link #close()} sends. The
 * box flashes flipped on this thread so far ride along in the same packet, after the furni states.
 */
public final class WiredFurniStateBatch {
    private static final ThreadLocal<Deque<Boolean>> DEPTH = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Map<Room, Map<Integer, HabboItem>>> PENDING =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private WiredFurniStateBatch() {}

    /** Opens a batch on this thread; every open needs its {@link #close()}. */
    public static void open() {
        DEPTH.get().push(Boolean.TRUE);
    }

    /** Closes the batch; the outermost close sends what it collected, one ObjectsDataUpdate per room. */
    public static void close() {
        Deque<Boolean> depth = DEPTH.get();
        if (depth.isEmpty()) return;
        depth.pop();
        if (!depth.isEmpty()) return;

        Map<Room, Map<Integer, HabboItem>> pending = PENDING.get();
        if (!pending.isEmpty()) {
            Map<Room, Map<Integer, HabboItem>> rooms = new LinkedHashMap<>(pending);
            pending.clear();
            for (Map.Entry<Room, Map<Integer, HabboItem>> entry : rooms.entrySet()) {
                // The furni states first, then the box flashes of the same firing, in one packet.
                LinkedHashSet<HabboItem> items =
                        new LinkedHashSet<>(entry.getValue().values());
                items.addAll(WiredBoxFlashes.takeDirty(entry.getKey()));
                entry.getKey().sendComposer(new ItemsDataUpdateComposer(items).compose());
            }
        }

        // Flashes of rooms without furni states still go out ahead of the firing's movements.
        WiredBoxFlashes.flushDirty();
    }

    public static boolean isOpen() {
        return !DEPTH.get().isEmpty();
    }

    /**
     * Drops the batch of the calling thread without sending what it collected. A thread whose wired work is torn down
     * (a shutdown, a pool thread being recycled) must not keep a batch open, or every furni state it writes afterwards
     * is held back instead of sent.
     */
    public static void clearForCurrentThread() {
        DEPTH.remove();
        PENDING.remove();
    }

    /**
     * Queues the state of {@code item} when a batch is open on this thread.
     *
     * @return whether it was queued; when not, the caller sends the state itself
     */
    static boolean defer(Room room, HabboItem item) {
        if (room == null || item == null || !isOpen() || !deferrable(item)) return false;

        PENDING.get().computeIfAbsent(room, r -> new LinkedHashMap<>()).put(item.getId(), item);
        return true;
    }

    /** Plain floor furni only: wall furni and limited furni keep their own update packets. */
    static boolean deferrable(HabboItem item) {
        return item.getBaseItem() != null && item.getBaseItem().getType() == FurnitureType.FLOOR && !item.isLimited();
    }
}
