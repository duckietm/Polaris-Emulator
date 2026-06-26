package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public class StaffOnlineCommand extends Command {
    public StaffOnlineCommand() {
        super("cmd_staffonline", Emulator.getTexts().getValue("commands.keys.cmd_staffonline").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        int minRank = Emulator.getConfig().getInt("commands.cmd_staffonline.min_rank");

        if (params.length >= 2) {
            try {
                int i = Integer.parseInt(params[1]);

                if (i < 1) {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_staffonline.positive_only"), RoomChatMessageBubbles.ALERT);
                    return true;
                } else {
                    minRank = i;
                }
            } catch (Exception e) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_staffonline.numbers_only"), RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        synchronized (Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos()) {
            var staffs = new ArrayList<Habbo>();

            for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().entrySet()) {
                if (set.getValue().getHabboInfo().getRank().getId() >= minRank) {
                    staffs.add(set.getValue());
                }
            }

            staffs.sort(new Comparator<Habbo>() {
                @Override
                public int compare(Habbo o1, Habbo o2) {
                    return o1.getHabboInfo().getId() - o2.getHabboInfo().getId();
                }
            });

            var message = new StringBuilder(Emulator.getTexts().getValue("commands.generic.cmd_staffonline.staffs"));
            message.append("\r\n");

            for (Habbo habbo : staffs) {
                message.append(habbo.getHabboInfo().getUsername());
                message.append(": ");
                message.append(habbo.getHabboInfo().getRank().getName());
                message.append("\r");
            }

            gameClient.getHabbo().alert(message.toString());
        }

        return true;
    }
}
