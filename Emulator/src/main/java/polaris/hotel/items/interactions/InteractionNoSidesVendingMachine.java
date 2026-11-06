package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;

public class InteractionNoSidesVendingMachine extends InteractionVendingMachine {
    public InteractionNoSidesVendingMachine(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionNoSidesVendingMachine(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public Set<RoomTile> getActivatorTiles(Room room) {

        Set<RoomTile> tiles = new HashSet<RoomTile>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                RoomTile tile = room.getLayout().getTile((short) (this.getX() + x), (short) (this.getY() + y));
                if (tile != null) {
                    tiles.add(tile);
                }
            }
        }

        return tiles;
    }
}
