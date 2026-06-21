package com.eu.habbo.threading.runnables.freeze;

import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.Set;

class FreezeResetExplosionTiles implements Runnable {
    private final Set<InteractionFreezeTile> tiles;
    private final Room room;

    public FreezeResetExplosionTiles(Set<InteractionFreezeTile> tiles, Room room) {
        this.tiles = tiles;
        this.room = room;
    }

    @Override
    public void run() {
        for (HabboItem item : this.tiles) {
            item.setExtradata("0");
            this.room.updateItem(item);
        }
    }
}
