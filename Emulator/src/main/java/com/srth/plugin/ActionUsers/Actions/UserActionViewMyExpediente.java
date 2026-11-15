package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Delitos.HabboEncarceledManager;

public class UserActionViewMyExpediente {

    public UserActionViewMyExpediente(Habbo habbo, int value) throws SQLException {

        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        try {

            new HabboEncarceledManager(habbo).showExpediente(habbo.getClient(), habbo);

        } catch (Exception e) {

        }
    }
}
