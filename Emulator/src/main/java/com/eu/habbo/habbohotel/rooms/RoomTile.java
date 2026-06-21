package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.Item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoomTile {
    public final short x;
    public final short y;
    public final short z;
    private final Set<RoomUnit> units;
    public RoomTileState state;
    private double stackHeight;
    private boolean allowStack = true;
    private RoomTile previous = null;
    private boolean diagonally;
    private short gCosts;
    private short hCosts;


    public RoomTile(short x, short y, short z, RoomTileState state, boolean allowStack) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.stackHeight = z;
        this.state = state;
        this.setAllowStack(allowStack);
        this.units = new HashSet<>();
    }

    public RoomTile(RoomTile tile) {
        this.x = tile.x;
        this.y = tile.y;
        this.z = tile.z;
        this.stackHeight = tile.stackHeight;
        this.state = tile.state;
        this.allowStack = tile.allowStack;
        this.diagonally = tile.diagonally;
        this.gCosts = tile.gCosts;
        this.hCosts = tile.hCosts;

        if (this.state == RoomTileState.INVALID) {
            this.allowStack = false;
        }
        this.units = tile.units;
    }

    public RoomTile()
    {
        x = 0;
        y = 0;
        z = 0;
        this.stackHeight = 0;
        this.state = RoomTileState.INVALID;
        this.allowStack = false;
        this.diagonally = false;
        this.gCosts = 0;
        this.hCosts = 0;
        this.units = null;
    }

    public void setStackHeight(double stackHeight) {
        if (this.state == RoomTileState.INVALID) {
            this.stackHeight = Short.MAX_VALUE;
            this.allowStack = false;
            return;
        }

        if (stackHeight >= 0 && stackHeight != Short.MAX_VALUE) {
            this.stackHeight = stackHeight;
            this.allowStack = true;
        } else {
            this.allowStack = false;
            this.stackHeight = this.z;
        }
    }

    public boolean getAllowStack() {
        if (this.state == RoomTileState.INVALID) {
            return false;
        }

        return this.allowStack;
    }

    public short relativeHeight() {
        if (this.state == RoomTileState.INVALID) {
            return Short.MAX_VALUE;
        } else if (!this.allowStack && (this.state == RoomTileState.BLOCKED || this.state == RoomTileState.SIT)) {
            return 64 * 256;
        }

        return this.allowStack ? (short) (this.getStackHeight() * 256.0) : 64 * 256;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RoomTile &&
                ((RoomTile) o).x == this.x &&
                ((RoomTile) o).y == this.y;
    }

    public RoomTile copy() {
        return new RoomTile(this);
    }

    public double distance(RoomTile roomTile) {
        double x = this.x - roomTile.x;
        double y = this.y - roomTile.y;
        return Math.sqrt(x * x + y * y);
    }

    public void isDiagonally(boolean isDiagonally) {
        this.diagonally = isDiagonally;
    }

    public int getfCosts() {
        return this.gCosts + this.hCosts;
    }

    public int getgCosts() {
        return this.gCosts;
    }

    public void setgCosts(RoomTile previousRoomTile) {
        this.setgCosts(previousRoomTile, this.diagonally ? RoomLayout.DIAGONALMOVEMENTCOST : RoomLayout.BASICMOVEMENTCOST);
    }

    private void setgCosts(short gCosts) {
        this.gCosts = gCosts;
    }

    public void setgCosts(RoomTile previousRoomTile, int basicCost) {
        this.setgCosts((short) (previousRoomTile.getgCosts() + basicCost));
    }

    public int calculategCosts(RoomTile previousRoomTile) {
        if (this.diagonally) {
            return previousRoomTile.getgCosts() + 14;
        }

        return previousRoomTile.getgCosts() + 10;
    }

    public void sethCosts(RoomTile parent, int cost) {
        this.hCosts = (short) ((Math.abs(this.x - parent.x) + Math.abs(this.y - parent.y)) * (cost));
    }

    public String toString() {
        return "RoomTile (" + this.x + ", " + this.y + ", " + this.z + "): h: " + this.hCosts + " g: " + this.gCosts + " f: " + this.getfCosts();
    }

    public boolean isWalkable() {
        return this.state == RoomTileState.OPEN;
    }

    public boolean is(short x, short y) {
        return this.x == x && this.y == y;
    }

    public List<RoomUnit> getUnits() {
        synchronized (this.units) {
            return new ArrayList<>(this.units);
        }
    }

    public void addUnit(RoomUnit unit) {
        synchronized (this.units) {
            if (!this.units.contains(unit)) {
                this.units.add(unit);
            }
        }
    }

    public void removeUnit(RoomUnit unit) {
        synchronized (this.units) {
            this.units.remove(unit);
        }
    }

    public boolean hasUnits() {
        synchronized(this.units) {
        if (!this.units.isEmpty()) {
            this.units.removeIf(unit -> !unit.getCurrentLocation().equals(this));
        }
        return !this.units.isEmpty();
        }
    }

    public boolean unitIsOnFurniOnTile(RoomUnit unit, Item item) {
        return (unit.getX() >= this.x && unit.getX() < this.x + item.getLength()) && (unit.getY() >= this.y && unit.getY() < this.y + item.getWidth());
    }

    public short getX() {
        return this.x;
    }

    public short getY() {
        return this.y;
    }

    public short getZ() {
        return this.z;
    }

    public short getGCost() {
        return this.gCosts;
    }

    public short getHCost() {
        return this.hCosts;
    }

    public void setDiagonally(boolean diagonally) {
        this.diagonally = diagonally;
    }

    public void setGCost(short gCost) {
        this.gCosts = gCost;
    }

    public void setHCost(short hCost) {
        this.hCosts = hCost;
    }

    public RoomTileState getState() {
        return this.state;
    }

    public double getStackHeight() {
        return this.stackHeight;
    }

    public RoomTile getPrevious() {
        return this.previous;
    }

    public boolean isDiagonally() {
        return this.diagonally;
    }

    public void setState(RoomTileState state) {
        this.state = state;
    }

    public void setAllowStack(boolean allowStack) {
        this.allowStack = allowStack;
    }

    public void setPrevious(RoomTile previous) {
        this.previous = previous;
    }
}
