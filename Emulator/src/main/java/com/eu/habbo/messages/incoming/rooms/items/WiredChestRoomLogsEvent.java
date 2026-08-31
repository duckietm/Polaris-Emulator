package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionLog;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.WiredChestRoomLogsComposer;

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
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || !room.hasRights(habbo)) return;

        int amount = this.packet.readInt();
        int page = this.packet.readInt();
        int filter = this.packet.readInt();

        // Appended, so a client that only knows how to ask about the whole room still can.
        int chestId = this.packet.bytesAvailable() >= 4 ? this.packet.readInt() : 0;

        this.client.sendResponse(
                new WiredChestRoomLogsComposer(ChestTransactionLog.page(room.getId(), filter, amount, page, chestId)));
    }
}
