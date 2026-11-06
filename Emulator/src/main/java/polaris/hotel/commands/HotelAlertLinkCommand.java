package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;

public class HotelAlertLinkCommand extends Command {
    public HotelAlertLinkCommand() {
        super("cmd_hal", Emulator.getTexts().getValue("commands.keys.cmd_hal").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 3) {
            return true;
        }

        String url = params[1];
        StringBuilder message = new StringBuilder();
        for (int i = 2; i < params.length; i++) {
            message.append(params[i]);
            message.append(" ");
        }

        message.append("\r\r-<b>")
                .append(gameClient.getUser().getUserInfo().getUsername())
                .append("</b>");

        Emulator.getGameServer()
                .getGameClientManager()
                .sendBroadcastResponse(new StaffAlertWithLinkComposer(message.toString(), url).compose());
        return true;
    }
}
