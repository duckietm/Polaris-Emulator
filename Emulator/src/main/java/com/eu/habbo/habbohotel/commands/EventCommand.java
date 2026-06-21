package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;

import java.util.HashMap;
import java.util.Map;

public class EventCommand extends Command {
    public EventCommand() {
        super("cmd_event", Emulator.getTexts().getValue("commands.keys.cmd_event").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            if (params.length >= 2) {
                StringBuilder message = new StringBuilder();

                for (int i = 1; i < params.length; i++) {
                    message.append(params[i]);
                    message.append(" ");
                }

                Map<String, String> codes = new HashMap<>();
                codes.put("ROOMNAME", gameClient.getHabbo().getHabboInfo().getCurrentRoom().getName());
                codes.put("ROOMID", gameClient.getHabbo().getHabboInfo().getCurrentRoom().getId() + "");
                codes.put("USERNAME", gameClient.getHabbo().getHabboInfo().getUsername());
                codes.put("LOOK", gameClient.getHabbo().getHabboInfo().getLook());
                codes.put("TIME", Emulator.getDate().toString());
                codes.put("MESSAGE", message.toString());

                ServerMessage msg = new BubbleAlertComposer("hotel.event", codes).compose();

                for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().entrySet()) {
                    Habbo habbo = set.getValue();
                    if (habbo.getHabboStats().blockStaffAlerts)
                        continue;

                    habbo.getClient().sendResponse(msg);
                }

                return true;
            }
        }

        return false;
    }
}
