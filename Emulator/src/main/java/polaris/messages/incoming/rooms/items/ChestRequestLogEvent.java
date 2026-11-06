package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.ChestLogComposer;

/**
 * Requests a wired chest's transaction log: {@code int itemId}. Responds with {@link ChestLogComposer}.
 */
public class ChestRequestLogEvent extends MessageHandler {
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

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        UserItem item = room.getUserItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        // The log carries other players' usernames and amounts, so gate it the same way the chest
        // itself opens — public chests anyone may view, private ones only for users with rights.
        if (!chest.getContents().isAccessOpen() && !room.hasRights(user)) return;

        this.client.sendResponse(new ChestLogComposer(chest));
    }
}
