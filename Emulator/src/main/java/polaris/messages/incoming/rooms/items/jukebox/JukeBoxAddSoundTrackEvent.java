package polaris.messages.incoming.rooms.items.jukebox;

import polaris.hotel.items.interactions.InteractionMusicDisc;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;

public class JukeBoxAddSoundTrackEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null || !room.hasRights(this.client.getUser())) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        this.packet.readInt(); // slotId

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        User user = this.client.getUser();

        if (user != null) {
            UserItem item = user.getInventory().getItemsComponent().getUserItem(itemId);

            if (item instanceof InteractionMusicDisc && item.getRoomId() == 0) {
                room.getTraxManager().addSong((InteractionMusicDisc) item, user);
            }
        }
    }
}
