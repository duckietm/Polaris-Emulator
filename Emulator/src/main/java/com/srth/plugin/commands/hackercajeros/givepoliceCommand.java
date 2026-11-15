package com.srth.plugin.commands.hackercajeros;

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

public class givepoliceCommand extends Command {

    public givepoliceCommand(String permission, String[] keys) {
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

                    habboRp.checkSettings("work_polices", 0);
                    habboRp.checkSettings("work_polices.its.active", 1);

                    if(habboRp.getBoolean("work_polices") == false){

                        estado = "active";
                        habboRp.updateData("work_polices", "1");

                        Emulator.getThreading().run(() -> {
                            // Show alert
                            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert("RoleplayWebsocketsRoomsInfo/job_polices/police_active.png", "work_police_cmd_give.recibe.active", habbo.getClient());
                        }, 200);

                        Emulator.getThreading().run(() -> {
                            // Show alert
                            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert("RoleplayWebsocketsRoomsInfo/job_polices/scanner_police.png", "work_police_cmd_give.recibe.active.accion", habbo.getClient());
                        }, 500);

                    } else {

                        estado = "disabled";
                        habboRp.updateData("work_polices", "0");

                        Emulator.getThreading().run(() -> {
                            // Show alert
                            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert("RoleplayWebsocketsRoomsInfo/job_polices/police_disabled.png", "work_police_cmd_give.recibe.disabled", habbo.getClient());
                        }, 200);


                        
                        if(habboRp.getBoolean("work_polices.its.active") == true){

                            habboRp.updateData("work_polices.its.active", "0");

                            habbo.getHabboInfo().setLook(RoleplayWebsocketsRoomsInfo.getConfigUser(habbo.getHabboInfo().getId()).getValue("global_ultimo_look_save"));
                            room.sendComposer(new RoomUserDataComposer(habbo).compose());
                            habbo.getClient().sendResponse(new UserDataComposer(habbo));

                            // Generar efecto
                            room.giveEffect(habbo,Integer.valueOf(0),-1);
                        }
                    }

                    // Mostrar alerta
                    THashMap<String, String> keys = new THashMap<>();
                    // Mostrar alerta
                    keys.put("display", "BUBBLE"); 
                    keys.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/job_polices/police_"+estado+".png");
                    keys.put("message",RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_police_cmd_give."+estado+"").replace("%username%", habbo.getHabboInfo().getUsername()));
                    gameClient.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
                    return true;

                } else {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("cmd_arrestar.error.not_found").replace("%username%", params[1]));
                    return true;
                }
            } else {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("cmd_arrestar.error.no_user"));
                return true;
            }
        }

        return false;
    }
}