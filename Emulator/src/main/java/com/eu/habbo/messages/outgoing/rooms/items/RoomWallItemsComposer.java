package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.Set;

public class RoomWallItemsComposer extends MessageComposer {
    private final Room room;

    public RoomWallItemsComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomWallItemsComposer);
        Int2ObjectMap<String> userNames = this.room.getFurniOwnerNames();

        this.response.appendInt(userNames.size());
        for (Int2ObjectMap.Entry<String> set : userNames.int2ObjectEntrySet()) {
            this.response.appendInt(set.getIntKey());
            this.response.appendString(set.getValue());
        }

        Set<HabboItem> items = this.room.getWallItems();

        this.response.appendInt(items.size());
        for (HabboItem item : items) {
            item.serializeWallData(this.response);
        }
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
