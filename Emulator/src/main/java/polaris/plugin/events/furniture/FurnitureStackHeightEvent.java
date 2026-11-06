package polaris.plugin.events.furniture;

import polaris.hotel.rooms.Room;
import polaris.plugin.Event;

public class FurnitureStackHeightEvent extends Event {

    public final short x;
    public final short y;
    public final Room room;
    private boolean pluginHelper;
    private Double height;

    public FurnitureStackHeightEvent(short x, short y, Room room) {
        this.x = x;
        this.y = y;
        this.room = room;
        this.pluginHelper = false;
        this.height = 0.0D;
    }

    public void setPluginHelper(boolean helper) {
        this.pluginHelper = helper;
    }

    public boolean hasPluginHelper() {
        return this.pluginHelper;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Double getHeight() {
        return this.height;
    }
}
