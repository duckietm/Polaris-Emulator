package com.eu.habbo.messages.incoming.rooms.items.jukebox;

import com.eu.habbo.habbohotel.items.interactions.InteractionMusicDisc;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;

public class JukeBoxAddSoundTrackEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || !room.hasRights(this.client.getHabbo())) return;

        int itemId = this.packet.readInt();
        this.packet.readInt(); // slotId

        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        Habbo habbo = this.client.getHabbo();

        if (habbo != null) {
            HabboItem item = habbo.getInventory().getItemsComponent().getHabboItem(itemId);

            if (item instanceof InteractionMusicDisc && item.getRoomId() == 0) {
                room.getTraxManager().addSong((InteractionMusicDisc) item, habbo);
            }
        }
    }
}
