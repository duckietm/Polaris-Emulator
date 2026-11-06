package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.ChestOpenHelper;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

/**
 * Player opens a wired chest (official Nod / header 806 wire shape, Nitro header 9327): {@code int chestItemId}.
 */
public class ChestOpenEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        // Opening streams the full contents in chunks; throttle to blunt request floods.
        return 250;
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

        ChestOpenHelper.sendContents(this.client, chest, room);
    }
}
