package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionRentableSpace;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;

import java.util.HashSet;
import java.util.Set;

public class ClearRentedSpace implements Runnable {
    private final InteractionRentableSpace item;
    private final Room room;

    public ClearRentedSpace(InteractionRentableSpace item, Room room) {
        this.item = item;
        this.room = room;
    }

    @Override
    public void run() {
        Set<HabboItem> items = new HashSet<>();

        for (RoomTile t : this.room.getLayout().getTilesAt(this.room.getLayout().getTile(this.item.getX(), this.item.getY()), this.item.getBaseItem().getWidth(), this.item.getBaseItem().getLength(), this.item.getRotation())) {
            for (HabboItem i : this.room.getItemsAt(t)) {
                if (i.getUserId() == this.item.getRenterId()) {
                    items.add(i);
                    i.setRoomId(0);
                    i.needsUpdate(true);
                }
            }
        }

        Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.item.getRenterId());

        if (owner != null) {
            owner.getClient().sendResponse(new AddHabboItemComposer(items));
            owner.getHabboStats().rentedItemId = 0;
            owner.getHabboStats().rentedTimeEnd = 0;
        } else {
            for (HabboItem i : items) {
                i.run();
            }
        }
    }
}
