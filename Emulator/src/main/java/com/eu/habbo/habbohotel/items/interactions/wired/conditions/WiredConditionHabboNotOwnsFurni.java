package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Negation of {@link WiredConditionHabboOwnsFurni}: passes when the triggering user does NOT own furni of
 * the selected type(s). Reuses the same HAS_ALTITUDE picker dialog and inventory check.
 */
public class WiredConditionHabboNotOwnsFurni extends WiredConditionHabboOwnsFurni {

    public WiredConditionHabboNotOwnsFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHabboNotOwnsFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return false;
        }

        this.refresh(room);

        THashSet<Integer> typeIds = this.resolveTypeIds(ctx);
        if (typeIds.isEmpty()) {
            return true;
        }

        List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER);
        if (users.isEmpty()) {
            return true;
        }

        for (RoomUnit unit : users) {
            Habbo habbo = room.getHabbo(unit);
            if (habbo != null && !this.userOwns(habbo, typeIds)) {
                return true;
            }
        }

        return false;
    }
}
