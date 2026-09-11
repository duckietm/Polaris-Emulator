package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionTrophy;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

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
        int itemId = this.packet.readInt();
        String text = this.packet.readString();

        Habbo habbo = this.client.getHabbo();
        Room room = this.currentRoom();

        if (habbo == null || room == null) return;

        HabboItem item = room.getHabboItem(itemId);

        if (item == null || item.getUserId() != habbo.getHabboInfo().getId()) return;

        if (item.getBaseItem().getInteractionType().getType() != InteractionTrophy.class) return;

        if (item.getExtradata() != null && !item.getExtradata().isEmpty()) return;

        item.setExtradata(Emulator.getGameEnvironment()
                .getCatalogManager()
                .prepareFurnitureExtraData(habbo, item.getBaseItem(), text));
        item.needsUpdate(true);
        room.updateItem(item);
        Emulator.getThreading().run(item);
    }
}
