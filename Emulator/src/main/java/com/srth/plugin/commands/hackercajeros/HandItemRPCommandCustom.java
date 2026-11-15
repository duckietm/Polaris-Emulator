package com.srth.plugin.commands.hackercajeros;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserHandItemComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class HandItemRPCommandCustom extends Command {

    public HandItemRPCommandCustom(String permission, String[] keys) {
        super(permission, keys);
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {

        if (params.length == 2) {

            Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

            if(room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")){

                gameClient.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.permited"));
                return false;
            }
            
            try {
                
                if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                    int effectId = Integer.parseInt(params[1]);
                    gameClient.getHabbo().getRoomUnit().setHandItem(effectId);
                    gameClient.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserHandItemComposer(gameClient.getHabbo().getRoomUnit()).compose());
                }
            } catch (Exception e) {
                //Don't handle incorrect parse exceptions :P
            }
        }
        return true;
    }
}
