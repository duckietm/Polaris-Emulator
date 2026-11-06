package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;

public class InteractionEffectVendingMachineNoSides extends InteractionVendingMachine {
    public InteractionEffectVendingMachineNoSides(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionEffectVendingMachineNoSides(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void giveVendingMachineItem(Room room, RoomUnit unit) {
        room.giveEffect(unit, this.getBaseItem().getRandomVendingItem(), 30);
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
