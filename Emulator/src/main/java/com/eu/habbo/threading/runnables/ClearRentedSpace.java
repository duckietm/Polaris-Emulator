package com.eu.habbo.threading.runnables;

import com.eu.habbo.habbohotel.items.interactions.InteractionRentableSpace;
import com.eu.habbo.habbohotel.rooms.Room;

/**
 * Ends an expired rent found when the space is loaded. It goes through {@link InteractionRentableSpace#endRent()}, which
 * picks the renter's items up the way the room does: this used to send an online renter the items without taking
 * them out of the room or putting them in the inventory.
 */
public class ClearRentedSpace implements Runnable {
    private final InteractionRentableSpace item;
    private final Room room;

    public ClearRentedSpace(InteractionRentableSpace item, Room room) {
        this.item = item;
        this.room = room;
    }

    @Override
    public void run() {
        if (this.item == null || this.room == null || this.item.getRenterId() <= 0) return;

        this.item.endRent();
    }
}
