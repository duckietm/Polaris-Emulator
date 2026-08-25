package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Passes when the resolved user has rights in the current room (owner, group rights, or explicitly
 * granted). Reuses the {@link WiredConditionHabboWearsBadge} dialog and serialization (badge-code text
 * field unused), so it needs no new client dialog.
 */
public class WiredConditionHabboHasRights extends WiredConditionHabboWearsBadge {

    public WiredConditionHabboHasRights(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboHasRights(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        Habbo habbo = room.getHabbo(roomUnit);
        return habbo != null && room.hasRights(habbo);
    }
}
