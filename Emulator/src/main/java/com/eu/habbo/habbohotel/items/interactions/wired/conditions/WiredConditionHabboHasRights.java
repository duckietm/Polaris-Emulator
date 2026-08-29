package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Passes when the resolved user has rights in the current room (owner, group rights, or explicitly
 * granted). It keeps {@link WiredConditionHabboWearsBadge}'s serialization — the user source and the
 * quantifier are the settings it needs — but answers {@link WiredConditionType#USER_ATTRIBUTE} so the
 * client opens the dialog without the badge-code field.
 */
public class WiredConditionHabboHasRights extends WiredConditionHabboWearsBadge {
    public static final WiredConditionType type = WiredConditionType.USER_ATTRIBUTE;

    public WiredConditionHabboHasRights(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboHasRights(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        Habbo habbo = room.getHabbo(roomUnit);
        return habbo != null && room.hasRights(habbo);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
