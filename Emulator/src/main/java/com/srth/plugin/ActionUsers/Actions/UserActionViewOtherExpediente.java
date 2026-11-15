package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Delitos.HabboEncarceledManager;
import com.srth.plugin.habbo.Jobs.Job;

public class UserActionViewOtherExpediente {

    public UserActionViewOtherExpediente(GameClient client, Habbo habbo) throws SQLException {

        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        HabboRp gameStats = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());
        if (gameStats.getJobActive() == null
                || gameStats.getJobActive() != null
                        && !gameStats.getJobActive().getName().equalsIgnoreCase("police")) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif", "cmd_arrestar.need.active",
                    gameStats.getHabbo().getClient());
            return;
        }

        new EventosEmpleosTutorial(client.getHabbo()).eventMirarExpedienteUsuario();

        new HabboEncarceledManager(client.getHabbo()).showExpediente(client, habbo);
        client.getHabbo()
                .shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_expediente.talk.show.other.expediente")
                        .replace("%username%", habbo.getHabboInfo().getUsername()), RoomChatMessageBubbles.NORMAL);

    }

}
