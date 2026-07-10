package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestLogComposer;

/**
 * Requests a wired chest's transaction log: {@code int itemId}. Responds with {@link ChestLogComposer}.
 */
public class ChestRequestLogEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;

        // The log carries other players' usernames and amounts, so gate it the same way the chest
        // itself opens — public chests anyone may view, private ones only for users with rights.
        if (!chest.getContents().isAccessOpen() && !room.hasRights(habbo)) return;

        this.client.sendResponse(new ChestLogComposer(chest));
    }
}
