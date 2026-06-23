package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Passes when the resolved user does NOT have rights in the current room (the negation of
 * {@link WiredConditionHabboHasRights}). Reuses the wears-badge dialog and serialization, so it needs
 * no new client dialog.
 */
public class WiredConditionHabboNotHasRights extends WiredConditionHabboHasRights {

    public WiredConditionHabboNotHasRights(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboNotHasRights(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        Habbo habbo = room.getHabbo(roomUnit);
        return habbo != null && !room.hasRights(habbo);
    }
}
