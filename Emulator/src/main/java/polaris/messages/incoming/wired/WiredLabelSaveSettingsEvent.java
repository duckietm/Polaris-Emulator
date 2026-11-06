package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.extra.WiredExtraLabel;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.label.WiredLabelSupport;
import polaris.messages.incoming.MessageHandler;

/** Official furniture label settings save (AIR 2970): furni id, colour, visibility, always open. */
public class WiredLabelSaveSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int furniId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int color = this.packet.readInt();
        int visibility = this.packet.readInt();
        boolean alwaysOpen = this.packet.readBoolean();
        Room room = currentRoom();

        if (room == null || !room.canModifyWired(this.client.getUser())) return;

        UserItem item = room.getUserItem(furniId);
        if (!(item instanceof WiredExtraLabel label)) return;

        label.setSettings(color, visibility, alwaysOpen);
        WiredLabelSupport.save(label);
        WiredLabelSupport.broadcastChanged(room, label);
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
