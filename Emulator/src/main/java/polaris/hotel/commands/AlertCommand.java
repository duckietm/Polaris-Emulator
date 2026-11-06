package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class AlertCommand extends Command {

    public AlertCommand() {
        super(
                "cmd_alert",
                Emulator.getTexts().getValue("commands.keys.cmd_alert").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (params.length < 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_alert.forgot_username"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (params.length < 3) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_alert.forgot_message"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetUsername = params[1];
        StringBuilder message = new StringBuilder();

        for (int i = 2; i < params.length; i++) {
            message.append(params[i]).append(" ");
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(targetUsername);

        if (user != null) {
            if (!CommandTargetGuard.canTarget(gameClient.getUser(), user)) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            user.alert(
                    message + "\r\n    -" + gameClient.getUser().getUserInfo().getUsername());
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_alert.message_send")
                                    .replace("%user%", targetUsername),
                            RoomChatMessageBubbles.ALERT);
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_alert.user_offline")
                                    .replace("%user%", targetUsername),
                            RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}
