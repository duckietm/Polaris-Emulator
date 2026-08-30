package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * The player closed a chest's window: {@code int itemId}.
 *
 * <p>Without this the lid would never come down again. The default appearance mode opens a chest
 * while someone is looking inside, and only the client knows when they stopped, so it says so — the
 * official does the same when its window hides.
 *
 * <p>A player who walks out of the room is closed out separately, because a client that crashes or
 * disconnects never gets to send this.
 */
public class ChestCloseEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        HabboItem item = room.getHabboItem(this.packet.readInt());
        if (!(item instanceof InteractionWiredChest chest)) return;

        chest.closeFor(habbo, room);
    }
}
