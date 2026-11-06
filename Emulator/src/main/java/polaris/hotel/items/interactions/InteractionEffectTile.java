package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.wired.core.WiredManager;

public class InteractionEffectTile extends InteractionPressurePlate {
    public InteractionEffectTile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionEffectTile(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        Emulator.getThreading().run(() -> updateState(room), 100);

        if (objects != null && objects.length > 0) {
            WiredManager.triggerUserWalksOff(room, roomUnit, this);
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalk(roomUnit, room, objects);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (roomUnit.getRoomUnitType() == RoomUnitType.USER) {
            User user = room.getUser(roomUnit);

            // A rider keeps the riding effect; the tile's effect would hide the horse.
            if (user != null && user.getUserInfo().getRiding() == null) {
                this.giveEffect(room, roomUnit, user.getUserInfo().getGender());
            }
        } else if (roomUnit.getRoomUnitType() == RoomUnitType.BOT) {
            Bot bot = room.getBot(roomUnit);

            if (bot != null) {
                this.giveEffect(room, roomUnit, bot.getGender());
            }
        }
    }

    private void giveEffect(Room room, RoomUnit roomUnit, UserGender gender) {
        if (gender.equals(UserGender.M)) {
            room.giveEffect(roomUnit, this.getBaseItem().getEffectM(), -1);
        } else {
            room.giveEffect(roomUnit, this.getBaseItem().getEffectF(), -1);
        }
    }

    @Override
    public boolean isUsable() {
        return false;
    }
}
