package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;

public class InteractionTrap extends InteractionDefault {
    public InteractionTrap(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionTrap(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if (!this.getExtradata().equals("0")) {
            User user = room.getUser(roomUnit);
            int effect = user.getClient().getUser().getRoomUnit().getEffectId();
            roomUnit.stopWalking();
            super.onWalkOn(roomUnit, room, objects);
            int delay = Emulator.getConfig()
                    .getInt("hotel.item.trap." + this.getBaseItem().getName());
            if (delay == 0) {
                Emulator.getConfig()
                        .register("hotel.item.trap." + this.getBaseItem().getName(), "3000");
                delay = 3000;
            }

            if (roomUnit != null) {
                if (this.getBaseItem().getEffectF() > 0 || this.getBaseItem().getEffectM() > 0) {
                    if (roomUnit.getRoomUnitType().equals(RoomUnitType.USER)) {

                        if (user != null) {
                            if (user.getUserInfo().getGender().equals(UserGender.M)
                                    && this.getBaseItem().getEffectM() > 0
                                    && user.getRoomUnit().getEffectId()
                                            != this.getBaseItem().getEffectM()) {
                                room.giveEffect(user, this.getBaseItem().getEffectM(), -1);
                                return;
                            }

                            if (user.getUserInfo().getGender().equals(UserGender.F)
                                    && this.getBaseItem().getEffectF() > 0
                                    && user.getRoomUnit().getEffectId()
                                            != this.getBaseItem().getEffectF()) {
                                room.giveEffect(user, this.getBaseItem().getEffectF(), -1);
                                return;
                            }

                            roomUnit.setCanWalk(false);
                            Emulator.getThreading()
                                    .run(
                                            () -> {
                                                room.giveEffect(roomUnit, 0, -1);
                                                roomUnit.setCanWalk(true);
                                                room.giveEffect(roomUnit, effect, -1);
                                            },
                                            delay);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}
}
