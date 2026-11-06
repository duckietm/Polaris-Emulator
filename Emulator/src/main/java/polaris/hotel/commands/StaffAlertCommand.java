package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.messenger.Message;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.messages.outgoing.friends.FriendChatMessageComposer;

public class StaffAlertCommand extends Command {
    public StaffAlertCommand() {
        super(
                "cmd_staffalert",
                Emulator.getTexts().getValue("commands.keys.cmd_staffalert").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length > 1) {
            StringBuilder message = new StringBuilder();
            for (int i = 1; i < params.length; i++) {
                message.append(params[i]).append(" ");
            }

            Emulator.getGameEnvironment()
                    .getUserManager()
                    .staffAlert(message + "\r\n-"
                            + gameClient.getUser().getUserInfo().getUsername());
            Emulator.getGameServer()
                    .getGameClientManager()
                    .sendBroadcastResponse(
                            new FriendChatMessageComposer(new Message(
                                            gameClient.getUser().getUserInfo().getId(), -1, message.toString()))
                                    .compose(),
                            "acc_staff_chat",
                            gameClient);
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_staffalert.forgot_message"),
                            RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
