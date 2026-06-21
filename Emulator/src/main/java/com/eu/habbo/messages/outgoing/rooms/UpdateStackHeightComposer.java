package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class UpdateStackHeightComposer extends MessageComposer {
    private int x;
    private int y;
    private short z;
    private double height;

    private Collection<RoomTile> updateTiles;
    private Room room;

    public UpdateStackHeightComposer(int x, int y, short z, double height) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.height = height;
    }

    public UpdateStackHeightComposer(Room room, Collection<RoomTile> updateTiles) {
        this.updateTiles = updateTiles;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UpdateStackHeightComposer);

        if (this.updateTiles != null) {
            List<RoomTile> tilesCopy = new ArrayList<>(this.updateTiles);
            tilesCopy.removeIf(Objects::isNull);

            if (tilesCopy.size() > 127) {
                this.response.appendByte(127);
                for (int i = 0; i < 127; i++) {
                    RoomTile t = tilesCopy.get(i);
                    this.response.appendByte((int) t.x);
                    this.response.appendByte((int) t.y);
                    if (Emulator.getConfig().getBoolean("custom.stacking.enabled")) {
                        this.response.appendShort((short) (t.z * 256.0));
                    } else {
                        this.response.appendShort(t.relativeHeight());
                    }
                }

                List<RoomTile> remainingTiles = tilesCopy.subList(127, tilesCopy.size());
                if (!remainingTiles.isEmpty()) {
                    this.room.sendComposer(new UpdateStackHeightComposer(this.room, new ArrayList<>(remainingTiles)).compose());
                }

                return this.response;
            }

            this.response.appendByte(tilesCopy.size());
            for (RoomTile t : tilesCopy) {
                this.response.appendByte((int) t.x);
                this.response.appendByte((int) t.y);
                if (Emulator.getConfig().getBoolean("custom.stacking.enabled")) {
                    this.response.appendShort((short) (t.z * 256.0));
                } else {
                    this.response.appendShort(t.relativeHeight());
                }
            }
        } else {
            this.response.appendByte(1);
            this.response.appendByte(this.x);
            this.response.appendByte(this.y);
            if (Emulator.getConfig().getBoolean("custom.stacking.enabled")) {
                this.response.appendShort((short) (this.z * 256.0));
            } else {
                this.response.appendShort((int) (this.height));
            }
        }

        return this.response;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public short getZ() {
        return z;
    }

    public double getHeight() {
        return height;
    }

    public Collection<RoomTile> getUpdateTiles() {
        return updateTiles;
    }

    public Room getRoom() {
        return room;
    }
}
