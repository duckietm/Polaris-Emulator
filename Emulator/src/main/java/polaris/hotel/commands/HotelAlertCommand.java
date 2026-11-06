package polaris.hotel.commands;

import java.util.Map;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;

public class HotelAlertCommand extends Command {

    public HotelAlertCommand() {
        super("cmd_ha", Emulator.getTexts().getValue("commands.keys.cmd_ha").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (params.length > 1) {
            StringBuilder message = new StringBuilder();
            for (int i = 1; i < params.length; i++) {
                message.append(params[i]).append(" ");
            }

            ServerMessage msg = new StaffAlertWithLinkComposer(
                            message + "\r\n-"
                                    + gameClient.getUser().getUserInfo().getUsername(),
                            "")
                    .compose();

            for (Map.Entry<Integer, User> set : Emulator.getGameEnvironment()
                    .getUserManager()
                    .getOnlineUsers()
                    .entrySet()) {
                User user = set.getValue();
                if (user.getUserStats().blockStaffAlerts) continue;

                user.getClient().sendResponse(msg);
            }
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ha.forgot_message"),
                            RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}
