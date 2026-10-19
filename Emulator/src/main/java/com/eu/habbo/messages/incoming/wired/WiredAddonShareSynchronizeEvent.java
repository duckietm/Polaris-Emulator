package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableAddonShare;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredVariableAddonSharing;
import com.eu.habbo.messages.incoming.MessageHandler;

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

        if (room == null || room.getRoomSpecialTypes() == null || !room.canModifyWired(this.client.getHabbo())) {
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
