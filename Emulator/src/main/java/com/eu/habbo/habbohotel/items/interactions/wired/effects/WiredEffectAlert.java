package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholderUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredEffectAlert extends WiredEffectWhisper {
    public WiredEffectAlert(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectAlert(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        List<RoomUnit> sourceUsers = resolveUsers(ctx);
        List<Habbo> recipients = resolveRecipients(ctx, sourceUsers);
        Habbo sharedSourceHabbo = (this.visibilitySelection == VISIBILITY_ALL_ROOM_USERS)
                ? resolveMessageSourceHabbo(ctx, sourceUsers)
                : null;

        for (Habbo habbo : recipients) {
            if (!shouldDeliverToRecipient(ctx, habbo)) {
                continue;
            }

            Habbo referenceHabbo = (sharedSourceHabbo != null) ? sharedSourceHabbo : habbo;
            String username = (referenceHabbo != null && referenceHabbo.getHabboInfo() != null)
                    ? referenceHabbo.getHabboInfo().getUsername()
                    : "";

            String message = this.message
                    .replace("%online%", Emulator.getGameEnvironment().getHabboManager().getOnlineCount() + "")
                    .replace("%username%", username)
                    .replace("%roomsloaded%", Emulator.getGameEnvironment().getRoomManager().loadedRoomsCount() + "");
            habbo.alert(WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, message));
        }
    }
}
