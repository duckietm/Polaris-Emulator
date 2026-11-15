package com.srth.plugin.interactions.vidayhambre.wireds.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionGroupMember;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionHabboVida extends WiredConditionGroupMember {
    public WiredConditionHabboVida(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboVida(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {

        int habbo = room.getHabbo(roomUnit).getHabboInfo().getId();
        return roomUnit != null && RoleplayWebsocketsRoomsInfo.getConfigUser(habbo).getInt("global.vida") >= 25;

    }

    @Override
    public WiredConditionOperator operator() {
        return WiredConditionOperator.OR;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }
}
