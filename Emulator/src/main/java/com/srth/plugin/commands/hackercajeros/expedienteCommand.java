package com.srth.plugin.commands.hackercajeros;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Delitos.HabboEncarceledManager;

public class expedienteCommand extends Command {

    public expedienteCommand(String permission, String[] keys) {
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
                
                if (habbo != null) 
                {

                    if(habbo != gameClient.getHabbo()){
                        if(!RoleplayWebsocketsRoomsInfo.getConfigUser(gameClient.getHabbo().getHabboInfo().getId()).getBoolean("work_polices") ||!RoleplayWebsocketsRoomsInfo.getConfigUser(gameClient.getHabbo().getHabboInfo().getId()).getBoolean("work_polices.its.active")){
                            gameClient.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_polices.need.active"), RoomChatMessageBubbles.NORMAL);
                            gameClient.getHabbo().talk(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_expediente.talk.show.error.expediente").replace("%username%", habbo.getHabboInfo().getUsername()), RoomChatMessageBubbles.NORMAL);
                            return false;
                        }
                    }

                    if(habbo == gameClient.getHabbo()){
                        new HabboEncarceledManager(gameClient.getHabbo()).showExpediente(gameClient, habbo);
                        gameClient.getHabbo().talk(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_expediente.talk.show.my.expediente"), RoomChatMessageBubbles.NORMAL);
                        return true;
                    }

                    new HabboEncarceledManager(gameClient.getHabbo()).showExpediente(gameClient, habbo);

                    Emulator.getThreading().run(() -> {
                        gameClient.getHabbo().shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_expediente.talk.show.other.expediente").replace("%username%", habbo.getHabboInfo().getUsername()), RoomChatMessageBubbles.NORMAL);
                    }, 1000);
                    
                    return true;

                } else {

                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("cmd_arrestar.error.not_found").replace("%username%", params[1]));

                    return true;
                }

            } else {

                new HabboEncarceledManager(gameClient.getHabbo()).showExpediente(gameClient, gameClient.getHabbo());

                gameClient.getHabbo().talk(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_expediente.talk.show.my.expediente"), RoomChatMessageBubbles.NORMAL);

                return true;
            }
        }
        
        return false;
    }
}