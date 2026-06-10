package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionOneWayGate;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class TriggerOneWayGateEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() == null)
            return;

        int itemId = this.packet.readInt();
        HabboItem item = this.client.getHabbo().getHabboInfo().getCurrentRoom().getHabboItem(itemId);

        if (item == null)
            return;

        if (item instanceof InteractionOneWayGate) {
            if (!item.getExtradata().equals("0") || this.client.getHabbo().getRoomUnit().isTeleporting)
                return;

            item.onClick(this.client, this.client.getHabbo().getHabboInfo().getCurrentRoom(), null);
        }

    }
}
