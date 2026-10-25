package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class PixelCommand extends Command {
    public PixelCommand() {
        super("cmd_duckets", text("commands.keys.cmd_duckets").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 3) {
            Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(params[1]);

            if (habbo != null) {
                try {
                    if (Integer.parseInt(params[2]) != 0) {
                        // Recorded as done by the staff member, and reported only when the ledger took it.
                        if (!CommandEconomy.points(
                                habbo,
                                0,
                                Integer.parseInt(params[2]),
                                "cmd_duckets",
                                gameClient.getHabbo().getHabboInfo().getId())) {
                            gameClient
                                    .getHabbo()
                                    .whisper(
                                            text(
                                                            "commands.error.cmd_duckets.failed",
                                                            "The duckets of %user% could not be changed.")
                                                    .replace("%user%", params[1]),
                                            RoomChatMessageBubbles.ALERT);
                            return true;
                        }
                        if (habbo.getHabboInfo().getCurrentRoom() != null)
                            habbo.whisper(
                                    text("commands.generic.cmd_duckets.received")
                                            .replace("%amount%", Integer.valueOf(params[2]) + ""),
                                    RoomChatMessageBubbles.ALERT);
                        else
                            habbo.alert(text("commands.generic.cmd_duckets.received")
                                    .replace("%amount%", Integer.valueOf(params[2]) + ""));

                        gameClient
                                .getHabbo()
                                .whisper(
                                        text("commands.succes.cmd_duckets.send")
                                                .replace("%amount%", Integer.valueOf(params[2]) + "")
                                                .replace("%user%", params[1]),
                                        RoomChatMessageBubbles.ALERT);

                    } else {
                        gameClient
                                .getHabbo()
                                .whisper(
                                        text("commands.error.cmd_duckets.invalid_amount"),
                                        RoomChatMessageBubbles.ALERT);
                    }
                } catch (NumberFormatException e) {
                    gameClient
                            .getHabbo()
                            .whisper(text("commands.error.cmd_duckets.invalid_amount"), RoomChatMessageBubbles.ALERT);
                }
            } else {
                gameClient
                        .getHabbo()
                        .whisper(
                                text("commands.error.cmd_duckets.user_offline").replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
            }
        } else {
            gameClient
                    .getHabbo()
                    .whisper(text("commands.error.cmd_duckets.invalid_amount"), RoomChatMessageBubbles.ALERT);
        }
        return true;
    }

    private static String text(String key) {
        return text(key, null);
    }

    private static String text(String key, String fallback) {
        return fallback == null
                ? Emulator.getTexts().getValue(key)
                : Emulator.getTexts().getValue(key, fallback);
    }
}
