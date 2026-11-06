package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;

public class InteractionEffectVendingMachine extends InteractionVendingMachine {
    public InteractionEffectVendingMachine(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionEffectVendingMachine(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void giveVendingMachineItem(Room room, RoomUnit unit) {
        room.giveEffect(unit, this.getBaseItem().getRandomVendingItem(), 30);
    }
}
