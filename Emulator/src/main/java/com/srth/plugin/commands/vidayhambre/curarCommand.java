package com.srth.plugin.commands.vidayhambre;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionUsers.Actions.UserActionCurar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class curarCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(curarCommand.class);

    public curarCommand(String permission, String[] keys) {
        super(permission, keys);
    }

    @Override
    public boolean handle(GameClient gameClient, String[] strings) throws Exception {
        try {

            final Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

            if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {
                gameClient.getHabbo()
                        .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
                return false;
            }

            if (strings.length >= 2) {

                Habbo habbo = room.getHabbo(strings[1]);

                if (habbo != null) {

                    new UserActionCurar(gameClient, habbo);

                } else {
                    gameClient.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts()
                            .getValue("cmd_curar.error.not_found").replace("%username%", strings[1]));
                }
            } else {
                gameClient.getHabbo()
                        .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_curar.error.no_user"));
            }

        } catch (Exception e) {
            LOGGER.error("Error while executing command", e);
        }

        return false;
    }
}