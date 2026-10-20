package com.eu.habbo.habbohotel.rooms;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The habbicon each user of a room shows over their head, for AIR's {@code @habbicon} variable: set when a user
 * triggers one or a wired box gives one, gone once the client's bubble has faded or a box removed it.
 */
public final class WiredHabbicons {
    /** How long the client shows a habbicon (AvatarLogic), so how long the user holds {@code @habbicon}. */
    public static final long VISIBLE_MS = 6_000L;

    private final Map<Integer, Shown> shown = new ConcurrentHashMap<>();

    private record Shown(int habbiconId, long untilMillis) {}

    /** Record that the room unit shows {@code habbiconId} from {@code nowMillis}; 0 or less clears it. */
    public void show(int roomUnitId, int habbiconId, long nowMillis) {
        if (habbiconId <= 0) {
            this.shown.remove(roomUnitId);
            return;
        }
        this.shown.put(roomUnitId, new Shown(habbiconId, nowMillis + VISIBLE_MS));
    }

    /** The habbicon the room unit shows at {@code nowMillis}, 0 when none. */
    public int current(int roomUnitId, long nowMillis) {
        Shown entry = this.shown.get(roomUnitId);
        if (entry == null) return 0;
        if (nowMillis >= entry.untilMillis()) {
            this.shown.remove(roomUnitId, entry);
            return 0;
        }
        return entry.habbiconId();
    }

    void clear() {
        this.shown.clear();
    }
}
