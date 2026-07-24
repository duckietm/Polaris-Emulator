package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionNotTriggerOnFurni extends WiredConditionTriggerOnFurni {
    public static final WiredConditionType type = WiredConditionType.NOT_ACTOR_ON_FURNI;

    public WiredConditionNotTriggerOnFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotTriggerOnFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        Room room = ctx.room();

        this.refresh();

        List<RoomUnit> userTargets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (userTargets.isEmpty()) return false;

        List<HabboItem> itemTargets = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (itemTargets.isEmpty()) return true;

        if (this.getQuantifier() == QUANTIFIER_ANY) {
            return !this.isAnyUserOnFurni(userTargets, itemTargets, room);
        }

        return !this.areAllUsersOnFurni(userTargets, itemTargets, room);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
