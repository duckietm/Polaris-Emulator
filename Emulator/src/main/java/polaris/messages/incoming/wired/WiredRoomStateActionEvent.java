package polaris.messages.incoming.wired;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

/**
 * Official AIR 13 wired settings room-state buttons (3761).
 *
 * <p>Both send everyone in the room out and bring them back two seconds later, so the room loads again from
 * storage. {@code false} is "reload", for anyone who may modify the room's wired. {@code true} is "roll back", for
 * the room owner and staff: every furni first goes back to its place and state from the last room load (a high
 * score board keeps its scores, a furni placed since goes back to where it was placed), and every wired box is
 * re-read from storage, which discards edits that were never saved.
 */
public class WiredRoomStateActionEvent extends MessageHandler {
    static final int FORWARD_DELAY_MS = 2000;

    @Override
    public void handle() throws Exception {
        boolean rollback = this.packet.readBoolean();
        Room room = currentRoom();

        if (room == null || !mayUse(room, this.client.getUser(), rollback)) {
            return;
        }

        if (rollback) {
            rollBack(room);
        }

        int roomId = room.getId();
        List<User> users = new ArrayList<>(room.getUsers());
        RoomManager rooms = roomManager();
        rooms.unloadRoom(room);

        Emulator.getThreading()
                .run(
                        () -> {
                            rooms.loadRoom(roomId);
                            ServerMessage message = new ForwardToRoomComposer(roomId).compose();
                            for (User user : users) {
                                if (returns(user)) user.getClient().sendResponse(message);
                            }
                        },
                        FORWARD_DELAY_MS);
    }

    /** Rolling back is for whoever manages the room's wired settings; reloading for whoever may modify its wired. */
    static boolean mayUse(Room room, User user, boolean rollback) {
        return rollback ? room.canManageWiredSettings(user) : room.canModifyWired(user);
    }

    /** Puts the furni back to their restore points and re-reads the wired boxes. */
    static int rollBack(Room room) {
        List<UserItem> items = new ArrayList<>(room.getFloorItems());
        items.addAll(room.getWallItems());
        int changed = room.getWiredRuntime() == null
                ? 0
                : room.getWiredRuntime().getRestorePoints().restore(items);
        room.reloadWiredData();
        return changed;
    }

    /** Someone who is still online and has not walked into another room meanwhile is brought back. */
    static boolean returns(User user) {
        return user != null
                && user.isOnline()
                && user.getClient() != null
                && user.getUserInfo() != null
                && user.getUserInfo().getCurrentRoom() == null;
    }

    private static RoomManager roomManager() {
        return Emulator.getGameEnvironment().getRoomManager();
    }

    @Override
    public int getRatelimit() {
        return 5000;
    }
}
