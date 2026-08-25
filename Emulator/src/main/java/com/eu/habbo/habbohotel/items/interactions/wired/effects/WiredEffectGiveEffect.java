package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectGiveEffect extends WiredEffectWhisper {
    public WiredEffectGiveEffect(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveEffect(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        int effectId;

        try {
            effectId = Integer.parseInt(this.message);
        } catch (Exception e) {
            return;
        }

        Room room = ctx.room();

        if (effectId >= 0) {
            for (RoomUnit roomUnit : resolveUsers(ctx)) {
                room.giveEffect(roomUnit, effectId, Integer.MAX_VALUE);
            }
        }
    }
}
