package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.ChestTransactionLog;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.WiredChestRoomLogsComposer;

/**
 * Requests one page of the room-wide wired chest transaction log for the chests tab. Reads
 * {@code int amount, int page, int filter} and, when the client asks about one chest rather than
 * the whole room, {@code int chestId} after them. Answers with {@link WiredChestRoomLogsComposer}.
 *
 * <p>The log names other players and the amounts they moved, so it is gated on room rights — the same
 * bar the room's wired configuration sits behind.
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
        if (room == null || !room.hasRights(user)) return;

        int amount = this.packet.readInt();
        int page = this.packet.readInt();
        int filter = this.packet.readInt();

        // Appended, so a client that only knows how to ask about the whole room still can.
        int chestId = this.packet.bytesAvailable() >= 4
                ? BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt())
                : 0;

        this.client.sendResponse(
                new WiredChestRoomLogsComposer(ChestTransactionLog.page(room.getId(), filter, amount, page, chestId)));
    }
}
