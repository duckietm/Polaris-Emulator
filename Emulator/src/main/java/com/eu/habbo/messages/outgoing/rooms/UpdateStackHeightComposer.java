package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.set.hash.THashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UpdateStackHeightComposer extends MessageComposer {
    private int x;
    private int y;
    private short z;
    private double height;

    private THashSet<RoomTile> updateTiles;
    private Room room;

    public UpdateStackHeightComposer(int x, int y, short z, double height) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.height = height;
    }

    public UpdateStackHeightComposer(Room room, THashSet<RoomTile> updateTiles) {
        this.updateTiles = updateTiles;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UpdateStackHeightComposer);

        // Read the synchronized Properties config once instead of once per tile in the loops below.
        final boolean customStacking = Emulator.getConfig().getBoolean("custom.stacking.enabled");

        if (this.updateTiles != null) {
            List<RoomTile> tilesCopy = new ArrayList<>(this.updateTiles);
            tilesCopy.removeIf(Objects::isNull);

            if (tilesCopy.size() > 127) {
                this.response.appendByte(127);
                for (int i = 0; i < 127; i++) {
                    RoomTile t = tilesCopy.get(i);
                    this.response.appendByte((int) t.x);
                    this.response.appendByte((int) t.y);
                    if (customStacking) {
                        this.response.appendShort((short) (t.z * 256.0));
                    } else {
                        this.response.appendShort(t.relativeHeight());
                    }
                }

                List<RoomTile> remainingTiles = tilesCopy.subList(127, tilesCopy.size());
                if (!remainingTiles.isEmpty()) {
                    this.room.sendComposer(new UpdateStackHeightComposer(this.room, new THashSet<>(remainingTiles)).compose());
                }

                return this.response;
            }

            this.response.appendByte(tilesCopy.size());
            for (RoomTile t : tilesCopy) {
                this.response.appendByte((int) t.x);
                this.response.appendByte((int) t.y);
                if (customStacking) {
                    this.response.appendShort((short) (t.z * 256.0));
                } else {
                    this.response.appendShort(t.relativeHeight());
                }
            }
        } else {
            this.response.appendByte(1);
            this.response.appendByte(this.x);
            this.response.appendByte(this.y);
            if (customStacking) {
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

    public THashSet<RoomTile> getUpdateTiles() {
        return updateTiles;
    }

    public Room getRoom() {
        return room;
    }
}
