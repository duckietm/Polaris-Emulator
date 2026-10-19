package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionRandomState;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UseRandomStateItemEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UseRandomStateItemEvent.class);

    @Override
    public void handle() throws Exception {
        try {
            int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
            this.packet.readInt(); // state

            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

            HabboItem item = room.getHabboItem(itemId);

            if (item == null || !(item instanceof InteractionRandomState)) return;

            InteractionRandomState randomStateItem = (InteractionRandomState) item;
            randomStateItem.onRandomStateClick(this.client, room);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }
}
