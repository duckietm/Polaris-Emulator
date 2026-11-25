package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.ChestTransactionLog;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.WiredChestRoomLogsComposer;

/**
 * Requests one page of the room-wide wired chest transaction log for the chests tab. Reads
 * {@code int amount, int page, int filter} and, when the client asks about one chest rather than
 * the whole room, {@code int chestId} after them. Answers with {@link WiredChestRoomLogsComposer}.
 *
 * <p>The log names other players and the amounts they moved, so the room's log is gated on room rights — the same
 * bar the room's wired configuration sits behind. One chest's log is also given to whoever may open that chest.
 */
public class WiredChestRoomLogsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        int amount = this.packet.readInt();
        int page = this.packet.readInt();
        int filter = this.packet.readInt();

        // Appended, so a client that only knows how to ask about the whole room still can.
        int chestId = this.packet.bytesAvailable() >= 4
                ? BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt())
                : 0;

        // The room's log is for users with rights. One chest's log is also for whoever may open that chest: its
        // "View logs" button asks for these pages, and an open chest shows its log to anyone, as its window does.
        if (!room.hasRights(user) && !mayReadChestLog(room, chestId)) return;

        this.client.sendResponse(new WiredChestRoomLogsComposer(
                ChestTransactionLog.transactions(room.getId(), filter, amount, page, chestId)));
    }

    /** Whether the log of this one chest is open to a user without rights: the chest exists here and is open to all. */
    static boolean mayReadChestLog(Room room, int chestId) {
        if (chestId <= 0) return false;

        UserItem item = room.getUserItem(chestId);
        return item instanceof InteractionWiredChest chest
                && chest.getContents().isAccessOpen();
    }
}
