package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.ChestDataComposer;

/**
 * Make a chest answer wired: {@code int itemId}.
 *
 * <p>A chest starts as storage. Its owner decides to make it part of the room's machinery, and from
 * then on wired effects and conditions can reach it. There is no message to undo this, deliberately:
 * a room built around a chest would break the moment somebody switched it off, and the client asks
 * for confirmation precisely because it is permanent.
 *
 * <p>The owner's decision, not the room's — someone with rights can use a chest, not repurpose it.
 */
public class ChestEnableWiredEvent extends MessageHandler {
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

        UserItem item = room.getUserItem(BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));
        if (!(item instanceof InteractionWiredChest chest)) return;
        if (user.getUserInfo().getId() != chest.getUserId()) return;

        // A starter chest is a taste of the feature, not a building block for a room.
        if (chest.isStarterChest()) return;
        if (chest.answersWired()) return;

        chest.getContents().enableWired();
        chest.persistContents(room);

        this.client.sendResponse(new ChestDataComposer(chest, user));
    }
}
