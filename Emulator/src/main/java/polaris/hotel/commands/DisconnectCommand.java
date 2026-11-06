package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.outgoing.handshake.DisconnectReasonComposer;

public class DisconnectCommand extends Command {
    public DisconnectCommand() {
        super(
                "cmd_disconnect",
                Emulator.getTexts().getValue("commands.keys.cmd_disconnect").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_disconnect.forgot_username"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params[1].equalsIgnoreCase(gameClient.getUser().getUserInfo().getUsername())) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_disconnect.disconnect_self"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        User target = Emulator.getGameEnvironment().getUserManager().getUser(params[1]);

        if (target == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_disconnect.user_offline"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!CommandTargetGuard.canTarget(gameClient.getUser(), target)) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_disconnect.higher_rank"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        // The reason tells the client not to reconnect on its own.
        target.getClient().sendResponse(new DisconnectReasonComposer(DisconnectReasonComposer.LOGOUT));
        target.getClient().getChannel().flush();
        target.getClient().getChannel().close();

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts()
                                .getValue("commands.succes.cmd_disconnect.disconnected")
                                .replace("%user%", params[1]),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
