package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Passes when the resolved user OWNS the given badge (in their inventory), not merely wears it. Reuses
 * the {@link WiredConditionHabboWearsBadge} dialog (ACTOR_WEARS_BADGE, badge-code text + source +
 * quantifier) and all of its serialization; the only difference is the owned-vs-worn check, so it needs
 * no new client dialog. This is the OWNED-badge condition that the Phase-A alias deliberately skipped
 * (the wears-badge class only checks worn badges).
 */
public class WiredConditionHabboOwnsBadge extends WiredConditionHabboWearsBadge {

    public WiredConditionHabboOwnsBadge(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboOwnsBadge(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null) {
            return false;
        }

        return habbo.getInventory().getBadgesComponent().hasBadge(this.badge);
    }
}
