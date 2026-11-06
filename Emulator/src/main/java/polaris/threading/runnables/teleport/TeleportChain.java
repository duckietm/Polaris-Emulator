package polaris.threading.runnables.teleport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredFreezeUtil;
import polaris.threading.runnables.UserItemNewState;

/**
 * Runs one step of a teleport. The steps used to hold the GameClient, which dispose() empties even when the session
 * is only suspended, so the next step threw and the chain stopped with isTeleporting set: the user could neither
 * walk nor teleport until they changed room, and the door stayed in its middle state.
 */
final class TeleportChain {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeleportChain.class);

    private TeleportChain() {}

    static void run(User user, UserItem teleport, Room room, Runnable step) {
        if (user == null || user.getRoomUnit() == null) {
            reset(teleport, room);
            return;
        }

        try {
            step.run();
        } catch (RuntimeException e) {
            LOGGER.error("Teleport of user {} stopped", user.getUserInfo().getId(), e);
            abort(user, teleport, room);
        }
    }

    /** Gives the user their walk back and the door its idle state. */
    static void abort(User user, UserItem teleport, Room room) {
        try {
            RoomUnit unit = user.getRoomUnit();

            if (unit != null) {
                unit.isTeleporting = false;
                WiredFreezeUtil.restoreWalkState(unit);
            }

            user.getUserInfo().setLoadingRoom(0);
        } finally {
            reset(teleport, room);
        }
    }

    private static void reset(UserItem teleport, Room room) {
        if (teleport == null || room == null) return;

        try {
            new UserItemNewState(teleport, room, "0").run();
        } catch (RuntimeException e) {
            LOGGER.error("Could not reset teleport {}", teleport.getId(), e);
        }
    }
}
