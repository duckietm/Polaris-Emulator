package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionHabboClubTeleportTile extends InteractionTeleportTile {
    public InteractionHabboClubTeleportTile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionHabboClubTeleportTile(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo != null) {
            return habbo.getHabboStats().hasActiveClub();
        }

        return false;
    }

    @Override
    public boolean canUseTeleport(GameClient client, Room room) {
        return super.canUseTeleport(client, room) && client.getHabbo().getHabboStats().hasActiveClub();
    }
}