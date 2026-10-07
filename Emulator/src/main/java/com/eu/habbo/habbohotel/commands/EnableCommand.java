package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnableCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnableCommand.class);

    public EnableCommand() {
        super(
                "cmd_enable",
                Emulator.getTexts().getValue("commands.keys.cmd_enable").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Habbo sender = gameClient.getHabbo();

        if (params.length >= 2) {
            int effectId;
            try {
                effectId = Integer.parseInt(params[1]);
            } catch (Exception e) {
                sender.whisperLocalized("commands.description.cmd_enable", RoomChatMessageBubbles.ALERT);
                return true;
            }
            Habbo target = sender;
            if (params.length == 3) {
                Room room = sender.getHabboInfo().getCurrentRoom();
                target = room == null ? null : room.getHabbo(params[2]);
            }

            if (target == null) {
                sender.whisperLocalized("generic.user.not_found", "%user%", params[2], RoomChatMessageBubbles.ALERT);
            } else if (target != sender && !sender.hasPermission(Permission.ACC_ENABLE_OTHERS)) {
                sender.whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
            } else {
                try {
                    if (target.getHabboInfo().getCurrentRoom() != null) {
                        if (target.getHabboInfo().getRiding() == null) {
                            if (Emulator.getGameEnvironment()
                                    .getPermissionsManager()
                                    .isEffectBlocked(
                                            effectId,
                                            target.getHabboInfo().getRank().getId())) {
                                gameClient
                                        .getHabbo()
                                        .whisper(
                                                Emulator.getTexts().getValue("commands.error.cmd_enable.not_allowed"),
                                                RoomChatMessageBubbles.ALERT);
                                return true;
                            }

                            target.getHabboInfo().getCurrentRoom().giveEffect(target, effectId, -1);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                }
            }
        } else {
            sender.whisperLocalized("commands.description.cmd_enable", RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}
