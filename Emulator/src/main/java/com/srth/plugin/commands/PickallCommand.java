package com.srth.plugin.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class PickallCommand extends Command {

    public PickallCommand() {
        super("cmd_pickall", Emulator.getTexts().getValue("commands.keys.cmd_pickall").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {

        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

        if (room != null) {

            if(room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")){
              HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(gameClient.getHabbo());
              habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/mapa1.png", RoleplayWebsocketsRoomsInfo.getTexts().getValue("no_cmd_pickall_rp"));
              return false;
            } else {

              if (room.isOwner(gameClient.getHabbo())) {
                room.ejectAll();
                return true;
              }

              room.ejectUserFurni(gameClient.getHabbo().getHabboInfo().getId());
            }
        }
        return false;
    }
}
