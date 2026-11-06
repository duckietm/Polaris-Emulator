package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.interfaces.ConditionalGate;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.threading.runnables.CloseGate;

public class InteractionGuildGate extends InteractionGuildFurni implements ConditionalGate {
    public InteractionGuildGate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionGuildGate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        if (roomUnit == null) return false;

        User user = room.getUser(roomUnit);

        return user != null
                && (user.getUserStats().hasGuild(super.getGuildId()) || user.hasPermission(Permission.ACC_GUILDGATE));
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.canWalkOn(roomUnit, room, objects)) {
            this.setExtradata("1");
            room.updateItemState(this);
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Emulator.getThreading().run(new CloseGate(this, room), 500);
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        this.setExtradata("0");
        room.updateItemState(this);
    }

    @Override
    public void onRejected(RoomUnit roomUnit, Room room, Object[] objects) {}
}
