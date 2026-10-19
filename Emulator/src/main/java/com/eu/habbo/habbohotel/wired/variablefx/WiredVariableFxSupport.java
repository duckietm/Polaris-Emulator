package com.eu.habbo.habbohotel.wired.variablefx;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredVariableAddonSharing;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link WiredVariableFxService} per room that has, or had, a variable fx box. The service is
 * started when an fx box is loaded or saved and lets go of itself once the room has neither boxes
 * nor viewers; a room being unloaded drops it outright.
 */
public final class WiredVariableFxSupport {
    private static final Map<Integer, WiredVariableFxService> SERVICES = new ConcurrentHashMap<>();

    private WiredVariableFxSupport() {}

    /** Makes sure the room's fx service is ticking. */
    public static void ensure(Room room) {
        if (room == null) return;

        SERVICES.computeIfAbsent(room.getId(), roomId -> {
            WiredVariableFxService service = new WiredVariableFxService(roomId);
            WiredManager.registerTickable(room, service);
            return service;
        });
    }

    /** The room has nothing left to show: stop ticking. */
    static void release(Room room, WiredVariableFxService service) {
        if (room == null || service == null) return;

        if (SERVICES.remove(room.getId(), service)) WiredManager.unregisterTickable(room, service);
    }

    /**
     * The room's viewers get every fx config and status again on the next flush (a Share box's Synchronize); a
     * room without a running service starts one when it imports fx.
     */
    public static void resync(Room room) {
        if (room == null) return;

        WiredVariableFxService service = SERVICES.get(room.getId());
        if (service != null) {
            service.requestResync();
        } else if (WiredVariableAddonSharing.importsFx(room)) {
            ensure(room);
        }
    }

    /** The room is being unloaded. */
    public static void drop(Room room) {
        if (room == null) return;

        SERVICES.remove(room.getId());
    }

    public static boolean isActive(int roomId) {
        return SERVICES.containsKey(roomId);
    }
}
