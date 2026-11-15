package com.srth.plugin.commands.hackercajeros;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionUsers.Actions.UserActionRobar;
import com.srth.plugin.habbo.HabboRp;

public class robarCommand extends Command {

    public robarCommand(String permission, String[] keys) {
        super(permission, keys);
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {

        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null) {

            Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

            if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {
                gameClient.getHabbo()
                        .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
                return false;
            }

            // Comprobaciones
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(gameClient.getHabbo());
            if (gameClient.getHabbo().getRoomUnit().getHandItem() != 2002) {
                RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlertItem("arma_ilegel.png",
                        "interactions_robar.pistola.disabled", gameClient);
                return false;
            }

            if (params.length >= 2) {
                Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(params[1]);
                if (habbo != null) {

                    new UserActionRobar(gameClient.getHabbo().getClient(), habbo, 1);

                }

            } else {
                gameClient.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts()
                        .getValue("cmd_robar.error.not_found").replace("%username%", params[1]));
                return true;
            }
        }

        return true;
    }
}