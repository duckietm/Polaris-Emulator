package polaris.messages.incoming.rooms.items;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionTrophy;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

/**
 * Engraves a mystery trophy: the owner clicks the blank trophy in the room, types a line and the
 * trophy keeps it forever. The text is written with the same owner, date and filter treatment a
 * trophy bought from the catalog gets, and a trophy that already carries a line is left alone.
 */
public class OpenMysteryTrophyEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String text = this.packet.readString();

        User user = this.client.getUser();
        Room room = this.currentRoom();

        if (user == null || room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (item == null || item.getUserId() != user.getUserInfo().getId()) return;

        if (item.getBaseItem().getInteractionType().getType() != InteractionTrophy.class) return;

        if (item.getExtradata() != null && !item.getExtradata().isEmpty()) return;

        item.setExtradata(Emulator.getGameEnvironment()
                .getCatalogManager()
                .prepareFurnitureExtraData(user, item.getBaseItem(), text));
        item.needsUpdate(true);
        room.updateItem(item);
        Emulator.getThreading().run(item);
    }
}
