package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;

import java.sql.ResultSet;
import java.sql.SQLException;

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
            Habbo habbo = room.getHabbo(roomUnit);
            int effect = habbo.getClient().getHabbo().getRoomUnit().getEffectId();
            roomUnit.stopWalking();
            super.onWalkOn(roomUnit, room, objects);
            int delay = Emulator.getConfig().getInt("hotel.item.trap." + this.getBaseItem().getName());
            if (delay == 0) {
                Emulator.getConfig().register("hotel.item.trap." + this.getBaseItem().getName(), "3000");
                delay = 3000;
            }

            if (roomUnit != null) {
                if (this.getBaseItem().getEffectF() > 0 || this.getBaseItem().getEffectM() > 0) {
                    if (roomUnit.getRoomUnitType().equals(RoomUnitType.USER)) {

                        if (habbo != null) {
                            if (habbo.getHabboInfo().getGender().equals(HabboGender.M) && this.getBaseItem().getEffectM() > 0 && habbo.getRoomUnit().getEffectId() != this.getBaseItem().getEffectM()) {
                                room.giveEffect(habbo, this.getBaseItem().getEffectM(), -1);
                                return;
                            }

                            if (habbo.getHabboInfo().getGender().equals(HabboGender.F) && this.getBaseItem().getEffectF() > 0 && habbo.getRoomUnit().getEffectId() != this.getBaseItem().getEffectF()) {
                                room.giveEffect(habbo, this.getBaseItem().getEffectF(), -1);
                                return;
                            }


                            roomUnit.setCanWalk(false);
                            Emulator.getThreading().run(() -> {
                                room.giveEffect(roomUnit, 0, -1);
                                roomUnit.setCanWalk(true);
                                room.giveEffect(roomUnit, effect, -1);
                            }, delay);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }
}
