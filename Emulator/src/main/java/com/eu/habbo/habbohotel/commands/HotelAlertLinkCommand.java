package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;

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
        var message = new StringBuilder();
        for (int i = 2; i < params.length; i++) {
            message.append(params[i]);
            message.append(" ");
        }

        message.append("\r\r-<b>").append(gameClient.getHabbo().getHabboInfo().getUsername()).append("</b>");

        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new StaffAlertWithLinkComposer(message.toString(), url).compose());
        return true;
    }
}