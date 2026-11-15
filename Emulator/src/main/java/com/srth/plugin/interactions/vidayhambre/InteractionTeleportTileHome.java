package com.srth.plugin.interactions.vidayhambre;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.interactions.vidayhambre.Teleports.InteractionTeleports;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionTeleportTileHome extends InteractionTeleports {
    public InteractionTeleportTileHome(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

    }

    public InteractionTeleportTileHome(int id, int userId, Item item, String extradata, int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if (roomUnit != null && this.canWalkOn(roomUnit, room, objects)) {
            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo != null) {
                if (!canUseTeleport(habbo.getClient(), room))
                    return;

                if (!habbo.getRoomUnit().isTeleporting) {

                    if(habbo.getRoomUnit().getCurrentLocation() == room.getLayout().getDoorTile()){
                        return;
                    }
                    
                    habbo.getRoomUnit().setGoalLocation(habbo.getRoomUnit().getCurrentLocation());
                    this.startTeleport(room, habbo, 1000);
                }
            }

        }
    }

}
