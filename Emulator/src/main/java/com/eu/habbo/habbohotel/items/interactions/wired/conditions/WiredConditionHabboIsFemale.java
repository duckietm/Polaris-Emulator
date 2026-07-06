package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Passes when the resolved user's gender is female. Reuses the {@link WiredConditionHabboWearsBadge}
 * dialog and serialization (badge-code text field unused), so it needs no new client dialog.
 */
public class WiredConditionHabboIsFemale extends WiredConditionHabboWearsBadge {

    public WiredConditionHabboIsFemale(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboIsFemale(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        Habbo habbo = room.getHabbo(roomUnit);
        return habbo != null && habbo.getHabboInfo().getGender() == HabboGender.F;
    }
}
