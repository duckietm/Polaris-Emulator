package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

import java.util.Map;
import java.util.NoSuchElementException;

public class RoomWallItemsComposer extends MessageComposer {
    private final Room room;

    public RoomWallItemsComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomWallItemsComposer);
        var userNames = new THashMap<Integer, String>();
        TIntObjectMap<String> furniOwnerNames = this.room.getFurniOwnerNames();
        TIntObjectIterator<String> iterator = furniOwnerNames.iterator();

        for (int i = furniOwnerNames.size(); i-- > 0; ) {
            try {
                iterator.advance();

                userNames.put(iterator.key(), iterator.value());
            } catch (NoSuchElementException e) {
                break;
            }
        }

        this.response.appendInt(userNames.size());
        for (Map.Entry<Integer, String> set : userNames.entrySet()) {
            this.response.appendInt(set.getKey());
            this.response.appendString(set.getValue());
        }

        THashSet<HabboItem> items = this.room.getWallItems();

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
