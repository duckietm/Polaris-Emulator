package com.eu.habbo.habbohotel.wired.leaderboard;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLeaderboard;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.wired.WiredLeaderboardSnapshotComposer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link WiredLeaderboardService} per room that has, or had, a leaderboard box. The service is
 * started when a box is loaded or saved and lets go of itself once the room has neither boxes nor
 * subscribers; a room being unloaded drops it outright. A room without one still answers a client's
 * hello with an empty snapshot, as the official client waits for one before it shows anything.
 */
public final class WiredLeaderboardSupport {
    private static final Map<Integer, WiredLeaderboardService> SERVICES = new ConcurrentHashMap<>();

    private WiredLeaderboardSupport() {}

    /** Makes sure the room's leaderboard service is ticking. */
    public static WiredLeaderboardService ensure(Room room) {
        if (room == null) return null;

        return SERVICES.computeIfAbsent(room.getId(), roomId -> {
            WiredLeaderboardService service = new WiredLeaderboardService(roomId);
            WiredManager.registerTickable(room, service);
            return service;
        });
    }

    /** The client entered the room and asks for its panels: {@code (0, 0, "0", [])}. */
    public static void hello(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getClient() == null) return;

        WiredLeaderboardService service = SERVICES.get(room.getId());
        if (service == null && hasBoxes(room)) service = ensure(room);

        if (service == null) {
            habbo.getClient().sendResponse(new WiredLeaderboardSnapshotComposer(List.of()));
            return;
        }
        service.hello(room, habbo);
    }

    /** A request for chunks of one panel; ignored when the room has no leaderboards. */
    public static void subscribe(
            Room room, Habbo habbo, int overlayId, int revision, String variableId, int[] chunkIndices) {
        if (room == null || habbo == null) return;

        WiredLeaderboardService service = SERVICES.get(room.getId());
        if (service != null) service.subscribe(room, habbo, overlayId, revision, variableId, chunkIndices);
    }

    /** The room has nothing left to show: stop ticking. */
    static void release(Room room, WiredLeaderboardService service) {
        if (room == null || service == null) return;

        if (SERVICES.remove(room.getId(), service)) WiredManager.unregisterTickable(room, service);
    }

    /** The room is being unloaded. */
    public static void drop(Room room) {
        if (room == null) return;

        SERVICES.remove(room.getId());
    }

    /** The panel number a published leaderboard box shows as in its room (from 1), or 0 when it is not shown. */
    public static int overlayIdOf(Room room, WiredExtraVariableLeaderboard box) {
        if (room == null || box == null || !box.isPublished()) return 0;

        return WiredLeaderboardService.overlayIdOf(room, box);
    }

    public static boolean isActive(int roomId) {
        return SERVICES.containsKey(roomId);
    }

    private static boolean hasBoxes(Room room) {
        if (room.getRoomSpecialTypes() == null) return false;

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableLeaderboard) return true;
        }
        return false;
    }
}
