package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;

public class SetMaxCommand extends Command {
    public SetMaxCommand() {
        super(
                "cmd_setmax",
                Emulator.getTexts().getValue("commands.keys.cmd_setmax").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length >= 2) {
            int max;
            try {
                max = Integer.parseInt(params[1]);
            } catch (Exception e) {
                // Not a number: same answer as an out of range one instead of saying the line.
                max = -1;
            }

            if (gameClient.getHabbo().getHabboInfo().getCurrentRoom() == null) {
                return true;
            }

            if (max > 0 && max < 9999) {
                gameClient.getHabbo().getHabboInfo().getCurrentRoom().setUsersMax(max);
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.success.cmd_setmax")
                                        .replace("%value%", max + ""),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_setmax.invalid_number"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        } else {
            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_setmax.forgot_number"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
