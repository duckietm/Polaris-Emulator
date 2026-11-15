package com.srth.plugin.commands.vidayhambre;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserDataComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

import gnu.trove.map.hash.THashMap;

public class giveMedicoCommand extends Command {

    public giveMedicoCommand(String permission, String[] keys) {
        super(permission, keys);
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {

        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null) {

            Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

            if(room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")){
                gameClient.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
                return false;
            }

            if (params.length >= 2) {

                Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(params[1]);
                
                if (habbo != null){

                    String estado = "";

                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                    habboRp.checkSettings("job_medico.permise_active", 0);
                    if(habboRp.getBoolean("job_medico.permise_active") == false){

                        estado = "active";
                        habboRp.updateData("job_medico.permise_active", "1");

                        Emulator.getThreading().run(() -> {
                            // Show alert
                            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert("RoleplayWebsocketsRoomsInfo/job_medico/work_active.png", "work_medico.user.enabled", habbo.getClient());
                        }, 200); 

                    } else {

                        estado = "disabled";
                        habboRp.updateData("job_medico.permise_active", "0");

                        Emulator.getThreading().run(() -> {
                            // Show alert
                            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_medico/work_disabled.png", 
                                "work_medico.user.disabled", 
                                habbo.getClient());
                        }, 200);
                    }

                    return true;

                } else {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("cmd_global.error.not_found").replace("%username%", params[1]));
                    return true;
                }
            } else {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("cmd_global.error.no_user"));
                return true;
            }
        }

        return false;
    }
}