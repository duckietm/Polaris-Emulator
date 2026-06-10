package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionColorWheel;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class TriggerColorWheelEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        HabboItem item = room.getHabboItem(itemId);

        if (item instanceof InteractionColorWheel) {
            item.onClick(this.client, room, null);
        }
    }
}
