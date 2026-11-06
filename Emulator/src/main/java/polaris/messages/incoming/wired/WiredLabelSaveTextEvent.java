package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.extra.WiredExtraLabel;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.label.WiredLabelSupport;
import polaris.messages.incoming.MessageHandler;

/** Official furniture label text save (AIR 2638): the furni id and the new text. */
public class WiredLabelSaveTextEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int furniId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String text = this.packet.readString();
        Room room = currentRoom();

        if (room == null || !room.canModifyWired(this.client.getUser())) return;

        UserItem item = room.getUserItem(furniId);
        if (!(item instanceof WiredExtraLabel label)) return;

        label.setText(text);
        WiredLabelSupport.save(label);
        WiredLabelSupport.broadcastChanged(room, label);
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
