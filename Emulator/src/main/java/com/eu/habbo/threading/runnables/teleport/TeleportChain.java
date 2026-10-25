package com.eu.habbo.threading.runnables.teleport;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.threading.runnables.HabboItemNewState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one step of a teleport. The steps used to hold the GameClient, which dispose() empties even when the session
 * is only suspended, so the next step threw and the chain stopped with isTeleporting set: the user could neither
 * walk nor teleport until they changed room, and the door stayed in its middle state.
 */
final class TeleportChain {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeleportChain.class);

    private TeleportChain() {}

    static void run(Habbo habbo, HabboItem teleport, Room room, Runnable step) {
        if (habbo == null || habbo.getRoomUnit() == null) {
            reset(teleport, room);
            return;
        }

        try {
            step.run();
        } catch (RuntimeException e) {
            LOGGER.error("Teleport of user {} stopped", habbo.getHabboInfo().getId(), e);
            abort(habbo, teleport, room);
        }
    }

    /** Gives the user their walk back and the door its idle state. */
    static void abort(Habbo habbo, HabboItem teleport, Room room) {
        try {
            RoomUnit unit = habbo.getRoomUnit();

            if (unit != null) {
                unit.isTeleporting = false;
                WiredFreezeUtil.restoreWalkState(unit);
            }

            habbo.getHabboInfo().setLoadingRoom(0);
        } finally {
            reset(teleport, room);
        }
    }

    private static void reset(HabboItem teleport, Room room) {
        if (teleport == null || room == null) return;

        try {
            new HabboItemNewState(teleport, room, "0").run();
        } catch (RuntimeException e) {
            LOGGER.error("Could not reset teleport {}", teleport.getId(), e);
        }
    }
}
