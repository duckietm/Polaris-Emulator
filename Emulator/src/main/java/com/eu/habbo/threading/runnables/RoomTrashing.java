package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemOnRollerComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;

import java.util.HashSet;
import java.util.Set;

public class RoomTrashing implements Runnable {
    public static RoomTrashing INSTANCE;

    private Habbo habbo;
    private Room room;

    public RoomTrashing(Habbo habbo, Room room) {
        this.habbo = habbo;
        this.room = room;

        RoomTrashing.INSTANCE = this;
    }

    @EventHandler
    public static void onUserWalkEvent(UserTakeStepEvent event) {
        if (INSTANCE == null)
            return;

        if (INSTANCE.habbo == null)
            return;

        if (!INSTANCE.habbo.isOnline())
            INSTANCE.habbo = null;

        if (INSTANCE.habbo == event.habbo) {
            if (event.habbo.getHabboInfo().getCurrentRoom() != null) {
                if (event.habbo.getHabboInfo().getCurrentRoom().equals(INSTANCE.room)) {
                    Set<ServerMessage> messages = new HashSet<>();

                    Set<HabboItem> items = INSTANCE.room.getItemsAt(event.toLocation);

                    int offset = Emulator.getRandom().nextInt(4) + 2;

                    RoomTile t = null;
                    while (offset > 0) {
                        t = INSTANCE.room.getLayout().getTileInFront(INSTANCE.room.getLayout().getTile(event.toLocation.x, event.toLocation.y), event.habbo.getRoomUnit().getBodyRotation().getValue(), (short) offset);

                        if (!INSTANCE.room.getLayout().tileWalkable(t.x, t.y)) {
                            offset--;
                        } else {
                            break;
                        }
                    }

                    for (HabboItem item : items) {
                        double offsetZ = (INSTANCE.room.getTopHeightAt(t.x, t.y)) - item.getZ();

                        messages.add(new FloorItemOnRollerComposer(item, null, t, offsetZ, INSTANCE.room).compose());
                    }


                    offset = Emulator.getRandom().nextInt(4) + 2;

                    t = null;
                    while (offset > 0) {
                        t = INSTANCE.room.getLayout().getTileInFront(INSTANCE.room.getLayout().getTile(event.toLocation.x, event.toLocation.y), event.habbo.getRoomUnit().getBodyRotation().getValue() + 7, (short) offset);

                        if (!INSTANCE.room.getLayout().tileWalkable(t.x, t.y)) {
                            offset--;
                        } else {
                            break;
                        }
                    }

                    RoomTile s = INSTANCE.room.getLayout().getTileInFront(INSTANCE.habbo.getRoomUnit().getCurrentLocation(), INSTANCE.habbo.getRoomUnit().getBodyRotation().getValue() + 7);

                    if (s != null) {
                        items = INSTANCE.room.getItemsAt(s);
                    }

                    for (HabboItem item : items) {
                        double offsetZ = (INSTANCE.room.getTopHeightAt(t.x, t.y)) - item.getZ();

                        messages.add(new FloorItemOnRollerComposer(item, null, t, offsetZ, INSTANCE.room).compose());
                    }

                    offset = Emulator.getRandom().nextInt(4) + 2;

                    t = null;
                    while (offset > 0) {
                        t = INSTANCE.getRoom().getLayout().getTileInFront(event.toLocation, event.habbo.getRoomUnit().getBodyRotation().getValue() + 1, (short) offset);

                        if (!INSTANCE.room.getLayout().tileWalkable(t.x, t.y)) {
                            offset--;
                        } else {
                            break;
                        }
                    }

                    s = INSTANCE.getRoom().getLayout().getTileInFront(INSTANCE.habbo.getRoomUnit().getCurrentLocation(), INSTANCE.habbo.getRoomUnit().getBodyRotation().getValue() + 1);
                    items = INSTANCE.room.getItemsAt(s);

                    for (HabboItem item : items) {
                        double offsetZ = (INSTANCE.room.getTopHeightAt(t.x, t.y)) - item.getZ();

                        messages.add(new FloorItemOnRollerComposer(item, null, t, offsetZ, INSTANCE.room).compose());
                    }

                    for (ServerMessage message : messages) {
                        INSTANCE.room.sendComposer(message);
                    }
                } else {
                    INSTANCE.habbo = null;
                    INSTANCE.room = null;
                }
            }
        }
    }

    @Override
    public void run() {

    }

    public Habbo getHabbo() {
        return this.habbo;
    }

    public void setHabbo(Habbo habbo) {
        this.habbo = habbo;
    }

    public Room getRoom() {
        return this.room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }
}
