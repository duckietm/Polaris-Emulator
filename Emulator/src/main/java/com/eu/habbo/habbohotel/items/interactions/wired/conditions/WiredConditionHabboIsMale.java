package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Passes when the resolved user's gender is male. It keeps {@link WiredConditionHabboWearsBadge}'s
 * serialization — the user source and the quantifier are exactly the settings this condition needs —
 * but answers {@link WiredConditionType#USER_ATTRIBUTE} so the client opens the dialog without the
 * badge-code field. Gender is not something you type in.
 */
public class WiredConditionHabboIsMale extends WiredConditionHabboWearsBadge {

    public WiredConditionHabboIsMale(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboIsMale(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        Habbo habbo = room.getHabbo(roomUnit);
        return habbo != null && habbo.getHabboInfo().getGender() == HabboGender.M;
    }

    @Override
    public WiredConditionType getType() {
        return WiredConditionType.USER_ATTRIBUTE;
    }
}
