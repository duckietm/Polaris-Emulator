package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;

/**
 * Saves a wired chest's notification prefs (room-rights only): {@code int itemId, bool full,
 * bool donation, bool withdraw, bool empty, bool wired, int mode}.
 */
public class ChestSaveNotificationsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();
        boolean full = this.packet.readBoolean();
        boolean donation = this.packet.readBoolean();
        boolean withdraw = this.packet.readBoolean();
        boolean empty = this.packet.readBoolean();
        boolean wired = this.packet.readBoolean();
        int mode = this.packet.readInt();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;
        if (!room.hasRights(habbo)) return;

        chest.getContents().setNotifications(full, donation, withdraw, empty, wired, mode);
        chest.persistContents();

        this.client.sendResponse(new ChestDataComposer(chest));
    }
}
