package com.eu.habbo.habbohotel.wired;

public class WiredMatchFurniSetting {
    public final int item_id;
    public final String state;
    public final int rotation;
    public final int x;
    public final int y;
    public final double z;

    public WiredMatchFurniSetting(int itemId, String state, int rotation, int x, int y) {
        this(itemId, state, rotation, x, y, 0.0D);
    }

    public WiredMatchFurniSetting(int itemId, String state, int rotation, int x, int y, double z) {
        this.item_id = itemId;
        this.state = state == null ? "" : state.replace("\t\t\t", " ");
        this.rotation = rotation;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public String toString() {
        return this.toString(true);
    }

    public String toString(boolean includeState) {
        return this.item_id + "-" + (this.state.isEmpty() || !includeState ? " " : this.state) + "-" + this.rotation + "-" + this.x + "-" + this.y + "-" + this.z;
    }

}
