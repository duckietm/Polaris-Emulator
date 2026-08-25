package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Passes when the resolved user's gender is male. Reuses the {@link WiredConditionHabboWearsBadge}
 * dialog and all of its serialization (the badge-code text field is unused), so it needs no new client
 * dialog — only the per-user check differs.
 */
public class WiredConditionHabboIsMale extends WiredConditionHabboWearsBadge {

    public WiredConditionHabboIsMale(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboIsMale(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        Habbo habbo = room.getHabbo(roomUnit);
        return habbo != null && habbo.getHabboInfo().getGender() == HabboGender.M;
    }
}
