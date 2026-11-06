package polaris.hotel.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class EnableCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnableCommand.class);

    public EnableCommand() {
        super(
                "cmd_enable",
                Emulator.getTexts().getValue("commands.keys.cmd_enable").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        User sender = gameClient.getUser();

        if (params.length >= 2) {
            int effectId;
            try {
                effectId = Integer.parseInt(params[1]);
            } catch (Exception e) {
                sender.whisperLocalized("commands.description.cmd_enable", RoomChatMessageBubbles.ALERT);
                return true;
            }
            User target = sender;
            if (params.length == 3) {
                Room room = sender.getUserInfo().getCurrentRoom();
                target = room == null ? null : room.getUser(params[2]);
            }

            if (target == null) {
                sender.whisperLocalized("generic.user.not_found", "%user%", params[2], RoomChatMessageBubbles.ALERT);
            } else if (target != sender && !sender.hasPermission(Permission.ACC_ENABLE_OTHERS)) {
                sender.whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
            } else {
                try {
                    if (target.getUserInfo().getCurrentRoom() != null) {
                        if (target.getUserInfo().getRiding() == null) {
                            if (Emulator.getGameEnvironment()
                                    .getPermissionsManager()
                                    .isEffectBlocked(
                                            effectId,
                                            target.getUserInfo().getRank().getId())) {
                                gameClient
                                        .getUser()
                                        .whisper(
                                                Emulator.getTexts().getValue("commands.error.cmd_enable.not_allowed"),
                                                RoomChatMessageBubbles.ALERT);
                                return true;
                            }

                            target.getUserInfo().getCurrentRoom().giveEffect(target, effectId, -1);
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
