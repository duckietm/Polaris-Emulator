package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;

import java.util.Map;

public class HotelAlertCommand extends Command {

    public HotelAlertCommand() {
        super("cmd_ha", Emulator.getTexts().getValue("commands.keys.cmd_ha").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (params.length > 1) {
            var message = new StringBuilder();
            for (int i = 1; i < params.length; i++) {
                message.append(params[i]).append(" ");
            }

            ServerMessage msg = new StaffAlertWithLinkComposer(message + "\r\n-" + gameClient.getHabbo().getHabboInfo().getUsername(), "").compose();

            for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().entrySet()) {
                Habbo habbo = set.getValue();
                if (habbo.getHabboStats().blockStaffAlerts)
                    continue;

                habbo.getClient().sendResponse(msg);
            }
        } else {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ha.forgot_message"), RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}
