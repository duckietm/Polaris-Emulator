package polaris.messages.incoming.rooms.items;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionMannequin;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class MannequinSaveNameEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null || !room.isOwner(this.client.getUser())) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        UserItem item = room.getUserItem(itemId);
        // Only a mannequin takes a look: other furni read their extradata as a number or a state.
        if (!(item instanceof InteractionMannequin)) return;

        String[] data = item.getExtradata().split(":");
        String name = RoomItemInputGuard.trimToMax(this.packet.readString(), 32);

        if (name.length() < 3 || name.length() > 15) {
            name = Emulator.getTexts().getValue("hotel.mannequin.name.default", "My look");
        }

        if (data.length == 3) {
            item.setExtradata(
                    this.client.getUser().getUserInfo().getGender().name().toUpperCase() + ":" + data[1] + ":" + name);
        } else {
            item.setExtradata(
                    this.client.getUser().getUserInfo().getGender().name().toUpperCase() + ":"
                            + this.client.getUser().getUserInfo().getLook() + ":" + name);
        }
        item.needsUpdate(true);
        Emulator.getThreading().run(item);
        room.updateItem(item);
    }
}
