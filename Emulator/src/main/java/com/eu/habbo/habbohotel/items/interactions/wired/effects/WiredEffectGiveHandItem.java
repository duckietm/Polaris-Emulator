package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectGiveHandItem extends WiredEffectWhisper {
    public WiredEffectGiveHandItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveHandItem(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        try {
            int itemId = Math.max(0, Integer.parseInt(this.message));

            Room room = ctx.room();

            for (com.eu.habbo.habbohotel.rooms.RoomUnit unit : resolveUsers(ctx)) {
                Habbo habbo = room.getHabbo(unit);
                if (habbo != null) {
                    room.giveHandItem(habbo, itemId);
                }
            }
        } catch (Exception e) {
            WiredCompatibilityDiagnostics.record(
                    WiredCompatibilityDiagnostics.FailurePoint.EFFECT_GIVE_HAND_ITEM,
                    ctx.room().getId(),
                    this.getId(),
                    e);
        }
    }
}
