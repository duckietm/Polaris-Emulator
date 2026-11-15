package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Delitos.HabboEncarceledManager;

public class UserActionEmptyOtherExpediente {

    public UserActionEmptyOtherExpediente(GameClient client, Habbo habbo, int value) throws SQLException {

        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        // Cuando no eres policia
        if (RoleplayWebsocketsRoomsInfo.getConfigUser(client.getHabbo().getHabboInfo().getId())
                .getBoolean("work_polices.its.active") == false) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif", "global_work_active.delito.disabled",
                    habbo.getClient());
            return;
        }

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        if (habboRp.checkSettings("work_polices.arrestos.stats", 0) >= 1) {

            habboRp.update("work_polices.arrestos.stats", "0");

            new HabboEncarceledManager(habbo).showExpediente(client, habbo);

            client.getHabbo()
                    .shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("user_action.emptyexpediente.msg.bubble")
                            .replace("%username%", habbo.getHabboInfo().getUsername()), RoomChatMessageBubbles.NORMAL);
        }

    }

}
