package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Delitos.HabboEncarceledManager;
import com.srth.plugin.habbo.Jobs.Job;

public class UserActionExitOtherPrision {

    public UserActionExitOtherPrision(GameClient client, Habbo habbo) throws SQLException {

        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());

        Job job_active = habboRp.getJobActive();
        if (job_active == null) {
            return;
        }

        // Cuando no eres policia
        if (job_active.getName().equalsIgnoreCase("police") == false) {
            client.getHabbo().whisper(
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_polices.need.active"),
                    RoomChatMessageBubbles.NORMAL);

            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif", "global_work_active.delito.disabled",
                    habbo.getClient());
            return;
        }

        HabboRp targetRP = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        if (targetRP.getDelitosHabbo().getEncarceled() == true) {

            client.getHabbo()
                    .shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("user_action.exitprision.msg.bubble")
                            .replace("%username%", habbo.getHabboInfo().getUsername()), RoomChatMessageBubbles.NORMAL);

            targetRP.updateData("general.task.active", "[{\"active\":0,\"task\":\"none\"}]");
            targetRP.getDelitosHabbo().resetDelitos();

            Emulator.getThreading().run(() -> {

                targetRP.getDelitosHabbo().exitPrision(); 
                targetRP.getDelitosHabbo().setEncarceled(false);

                habbo.getClient().sendResponse(new ForwardToRoomComposer(room.getId()));

                RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                        "RoleplayWebsocketsRoomsInfo/job_polices/arresto_false.png",
                        "action_encarceled.notif.time.success", habbo.getClient());

            }, 500);

        }

    }

}
