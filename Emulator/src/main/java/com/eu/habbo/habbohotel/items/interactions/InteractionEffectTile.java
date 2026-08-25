package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.sql.ResultSet;
import java.sql.SQLException;

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

        if(objects != null && objects.length > 0) {
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
            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo != null) {
                this.giveEffect(room, roomUnit, habbo.getHabboInfo().getGender());
            }
        } else if (roomUnit.getRoomUnitType() == RoomUnitType.BOT) {
            Bot bot = room.getBot(roomUnit);

            if (bot != null) {
                this.giveEffect(room, roomUnit, bot.getGender());
            }
        }
    }

    private void giveEffect(Room room, RoomUnit roomUnit, HabboGender gender) {
        if (gender.equals(HabboGender.M)) {
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
