package com.srth.plugin.interactions.vidayhambre;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.habboway.nux.NuxAlertComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionRoperoRP extends InteractionTeleport {

    public InteractionRoperoRP(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

    }

    public InteractionRoperoRP(int id, int userId, Item item, String extradata, int limitedStack,
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

                if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id")) {
                    return;
                }

                habbo.getClient().sendResponse(new NuxAlertComposer("avatar-editor/show"));
            }

        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if (roomUnit != null && this.canWalkOn(roomUnit, room, objects)) {
            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo != null) {

                if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id")) {
                    return;
                }

                habbo.getClient().sendResponse(new NuxAlertComposer("avatar-editor/hide"));
            }

        }
    }

}
