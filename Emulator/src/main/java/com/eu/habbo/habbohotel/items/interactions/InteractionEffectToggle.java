package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboGender;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionEffectToggle extends InteractionDefault {
    public InteractionEffectToggle(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionEffectToggle(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (this.getExtradata().isEmpty()) {
            this.setExtradata("0");
        }

        if (client != null) {
            if (room.hasRights(client.getHabbo())) {
                if (Integer.parseInt(this.getExtradata()) < this.getBaseItem().getStateCount() - 1) {
                    if ((client.getHabbo().getHabboInfo().getGender() == HabboGender.M && client.getHabbo().getRoomUnit().getEffectId() == this.getBaseItem().getEffectM()) ||
                            (client.getHabbo().getHabboInfo().getGender() == HabboGender.F && client.getHabbo().getRoomUnit().getEffectId() == this.getBaseItem().getEffectF())) {
                        super.onClick(client, room, objects);
                    }
                }
            }
        }
    }
}