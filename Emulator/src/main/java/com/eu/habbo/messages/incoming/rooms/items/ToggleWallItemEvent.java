package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurnitureToggleEvent;

public class ToggleWallItemEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        int itemId = this.packet.readInt();
        int state = this.packet.readInt();

        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        HabboItem item = room.getHabboItem(itemId);

        if (item == null)
            return;

        WiredManager.cancelPendingUserClicksFurni(room, this.client.getHabbo().getRoomUnit(), item);

        Event furnitureToggleEvent = new FurnitureToggleEvent(item, this.client.getHabbo(), state);
        Emulator.getPluginManager().fireEvent(furnitureToggleEvent);

        if (furnitureToggleEvent.isCancelled())
            return;

        if (item.getBaseItem().getName().equalsIgnoreCase("poster"))
            return;

        item.needsUpdate(true);
        item.onClick(this.client, room, new Object[]{state});
        room.updateItem(item);
        Emulator.getThreading().run(item);
    }
}
