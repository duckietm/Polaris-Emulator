package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableAddonShare;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredVariableAddonSharing;
import polaris.messages.incoming.MessageHandler;

/**
 * The Variable Add-on Share box's Synchronize button (AIR {@code §_-Q1S§}, {@code [wiredId]}; ours 9495 because AIR's
 * 2144 is the colour wheel). Only someone with wired write permission in the box's room may press it; the button is
 * disabled for anyone else.
 */
public class WiredAddonShareSynchronizeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        int wiredId = BuildersClubItemIds.fromWire(room, this.packet.readInt());

        if (room == null || room.getRoomSpecialTypes() == null || !room.canModifyWired(this.client.getUser())) {
            return;
        }

        InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(wiredId);
        if (extra instanceof WiredExtraVariableAddonShare share) {
            WiredVariableAddonSharing.synchronize(room, share);
        }
    }

    @Override
    public int getRatelimit() {
        return 5000;
    }
}
