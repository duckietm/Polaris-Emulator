package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

/**
 * The player closed a chest's window: {@code int itemId}.
 *
 * <p>Without this the lid would never come down again. The default appearance mode opens a chest
 * while someone is looking inside, and only the client knows when they stopped, so it says so — the
 * official does the same when its window hides.
 *
 * <p>A player who walks out of the room is closed out separately, because a client that crashes or
 * disconnects never gets to send this.
 */
public class ChestCloseEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        UserItem item = room.getUserItem(BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));
        if (!(item instanceof InteractionWiredChest chest)) return;

        chest.closeFor(user, room);
    }

    @Override
    public int getRatelimit() {
        // Closing broadcasts the chest state to the room; alternating open/close must not flood it.
        return 250;
    }
}
