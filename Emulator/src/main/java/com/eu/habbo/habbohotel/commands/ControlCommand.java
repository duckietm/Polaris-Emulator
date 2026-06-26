package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class ControlCommand extends Command {
    public ControlCommand() {
        super("cmd_control", Emulator.getTexts().getValue("commands.keys.cmd_control").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            if (params.length == 2) {
                Habbo target = gameClient.getHabbo().getHabboInfo().getCurrentRoom().getHabbo(params[1]);

                if (target == null) {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_control.not_found").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
                    return true;
                }

                if (target == gameClient.getHabbo()) {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_control.not_self"), RoomChatMessageBubbles.ALERT);
                    return true;
                }

                var oldHabbo = (Habbo) gameClient.getHabbo().getRoomUnit().getCacheable().remove("control");

                if (oldHabbo != null) {
                    oldHabbo.getRoomUnit().getCacheable().remove("controller");
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_control.stopped").replace("%user%", oldHabbo.getHabboInfo().getUsername()), RoomChatMessageBubbles.ALERT);
                }
                gameClient.getHabbo().getRoomUnit().getCacheable().put("control", target);
                target.getRoomUnit().getCacheable().put("controller", gameClient.getHabbo());
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_control.controlling").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
                return true;
            } else {
                Object habbo = gameClient.getHabbo().getRoomUnit().getCacheable().get("control");

                if (habbo != null) {
                    gameClient.getHabbo().getRoomUnit().getCacheable().remove("control");

                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_control.stopped").replace("%user%", ((Habbo) habbo).getHabboInfo().getUsername()), RoomChatMessageBubbles.ALERT);
                }
                return true;
            }
        }

        return true;
    }
}
