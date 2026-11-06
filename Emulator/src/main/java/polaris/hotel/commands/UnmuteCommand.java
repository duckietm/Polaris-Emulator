package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class UnmuteCommand extends Command {
    public UnmuteCommand() {
        super(
                "cmd_unmute",
                Emulator.getTexts().getValue("commands.keys.cmd_unmute").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_unmute.not_specified"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(params[1]);

        if (user == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_unmute.not_found")
                                    .replace("%user%", params[1]),
                            RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            if (!CommandTargetGuard.canTarget(gameClient.getUser(), user)) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (!user.getUserStats().allowTalk()
                    || (user.getUserInfo().getCurrentRoom() != null
                            && user.getUserInfo().getCurrentRoom().isMuted(user))) {
                if (!user.getUserStats().allowTalk()) {
                    user.unMute();
                }

                if (user.getUserInfo().getCurrentRoom() != null
                        && user.getUserInfo().getCurrentRoom().isMuted(user)) {
                    user.getUserInfo().getCurrentRoom().unmuteUser(user);
                }

                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.succes.cmd_unmute")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
            } else {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_unmute.not_muted")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        return true;
    }
}
