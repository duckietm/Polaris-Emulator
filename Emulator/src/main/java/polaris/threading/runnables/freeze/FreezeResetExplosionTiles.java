package polaris.threading.runnables.freeze;

import java.util.Set;
import polaris.hotel.items.interactions.games.freeze.InteractionFreezeTile;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;

class FreezeResetExplosionTiles implements Runnable {
    private final Set<InteractionFreezeTile> tiles;
    private final Room room;

    public FreezeResetExplosionTiles(Set<InteractionFreezeTile> tiles, Room room) {
        this.tiles = tiles;
        this.room = room;
    }

    @Override
    public void run() {
        for (UserItem item : this.tiles) {
            item.setExtradata("0");
            this.room.updateItem(item);
        }
    }
}
