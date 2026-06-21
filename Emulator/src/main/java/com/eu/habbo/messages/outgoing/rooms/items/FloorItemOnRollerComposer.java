package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Set;

public class FloorItemOnRollerComposer extends MessageComposer {
    private final HabboItem item;
    private final HabboItem roller;
    private final RoomTile oldLocation;
    private final RoomTile newLocation;
    private final double heightOffset;
    private final double oldZ;
    private final double newZ;
    private final Room room;

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile newLocation, double heightOffset, Room room) {
        this.item = item;
        this.roller = roller;
        this.newLocation = newLocation;
        this.heightOffset = heightOffset;
        this.room = room;
        this.oldLocation = null;
        this.oldZ = -1;
        this.newZ = -1;
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, double heightOffset, Room room) {
        this.item = item;
        this.roller = roller;
        this.oldLocation = oldLocation;
        this.oldZ = oldZ;
        this.newLocation = newLocation;
        this.newZ = newZ;
        this.heightOffset = heightOffset;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        short oldX = this.item.getX();
        short oldY = this.item.getY();

        this.response.init(Outgoing.ObjectOnRollerComposer);
        this.response.appendInt(this.oldLocation != null ? this.oldLocation.x : this.item.getX());
        this.response.appendInt(this.oldLocation != null ? this.oldLocation.y : this.item.getY());
        this.response.appendInt(this.newLocation.x);
        this.response.appendInt(this.newLocation.y);
        this.response.appendInt(1);
        this.response.appendInt(this.item.getId());
        this.response.appendString(Double.toString(this.oldLocation != null ? this.oldZ : this.item.getZ()));
        this.response.appendString(Double.toString(this.oldLocation != null ? this.newZ : (this.item.getZ() + this.heightOffset)));
        this.response.appendInt(this.roller != null ? this.roller.getId() : -1);

        if(this.oldLocation == null) {
            this.item.onMove(this.room, this.room.getLayout().getTile(this.item.getX(), this.item.getY()), this.newLocation);
            this.item.setX(this.newLocation.x);
            this.item.setY(this.newLocation.y);
            this.item.setZ(this.item.getZ() + this.heightOffset);
            this.item.needsUpdate(true);

            // Update affected tiles for both old and new positions
            Set<RoomTile> tiles = this.room.getLayout().getTilesAt(this.room.getLayout().getTile(oldX, oldY), this.item.getBaseItem().getWidth(), this.item.getBaseItem().getLength(), this.item.getRotation());
            tiles.addAll(this.room.getLayout().getTilesAt(this.room.getLayout().getTile(this.item.getX(), this.item.getY()), this.item.getBaseItem().getWidth(), this.item.getBaseItem().getLength(), this.item.getRotation()));
            this.room.updateTiles(tiles);
        }

        return this.response;
    }

    public HabboItem getItem() {
        return item;
    }

    public HabboItem getRoller() {
        return roller;
    }

    public RoomTile getOldLocation() {
        return oldLocation;
    }

    public RoomTile getNewLocation() {
        return newLocation;
    }

    public double getHeightOffset() {
        return heightOffset;
    }

    public double getOldZ() {
        return oldZ;
    }

    public double getNewZ() {
        return newZ;
    }

    public Room getRoom() {
        return room;
    }
}
