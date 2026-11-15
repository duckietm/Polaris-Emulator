package com.srth.plugin.commands.vidayhambre;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.ActionUsers.Actions.UserActionComer;
import com.srth.plugin.habbo.HabboRp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class comerCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(comerCommand.class);

    public comerCommand(String permission, String[] keys) {
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

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(gameClient.getHabbo());

            HabboTutorial habboTutorial = habboRp.getHabboTutorial();
            if (habboTutorial.getParteTutorial() < 10) {
                return false;
            }

            new UserActionComer(gameClient.getHabbo(), 1);

        } catch (Exception e) {
            LOGGER.error("Error while executing command", e);
        }

        return false;
    }
}