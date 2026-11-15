package com.srth.plugin.commands.hackercajeros;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnableRPCommandCustom extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnableRPCommandCustom.class);

    public EnableRPCommandCustom(String permission, String[] keys) {
        super(permission, keys);
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {

        if (params.length >= 2) {

            Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

            if(room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")){
                gameClient.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.permited"));
                return false;
            }

            int effectId;
            try {
                effectId = Integer.parseInt(params[1]);
            } catch (Exception e) {
                return false;
            }
            Habbo target = gameClient.getHabbo();
            if (params.length == 3) {
                target = gameClient.getHabbo().getHabboInfo().getCurrentRoom().getHabbo(params[2]);
            }

            if (target != null) {
                if (target == gameClient.getHabbo() || gameClient.getHabbo().hasPermission(Permission.ACC_ENABLE_OTHERS)) {
                    try {
                        if (target.getHabboInfo().getCurrentRoom() != null) {
                            if (target.getHabboInfo().getRiding() == null) {
                                if (Emulator.getGameEnvironment().getPermissionsManager().isEffectBlocked(effectId, target.getHabboInfo().getRank().getId())) {
                                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_enable.not_allowed"), RoomChatMessageBubbles.ALERT);
                                    return true;
                                }

                                target.getHabboInfo().getCurrentRoom().giveEffect(target, effectId, -1);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                }
            }
        }
        return true;
    }
}
