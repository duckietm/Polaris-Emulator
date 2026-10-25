package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

public class FreezeCommand extends Command {
    public FreezeCommand() {
        super(
                "cmd_freeze",
                Emulator.getTexts().getValue("commands.keys.cmd_freeze").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            Habbo habbo = gameClient.getHabbo().getHabboInfo().getCurrentRoom().getHabbo(params[1]);

            if (habbo == null) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_freeze.not_found")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else {
                if (CommandTargetGuard.refuse(gameClient.getHabbo(), habbo)) {
                    return true;
                }

                if (habbo.getRoomUnit().canWalk()) {
                    habbo.getRoomUnit().setCanWalk(false);
                    habbo.whisper(
                            Emulator.getTexts().getValue("commands.succes.cmd_freeze.frozen"),
                            RoomChatMessageBubbles.ALERT);
                    gameClient
                            .getHabbo()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_freeze.user_frozen")
                                            .replace("%user%", params[1]),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                } else {
                    habbo.getRoomUnit().setCanWalk(true);
                    habbo.whisper(
                            Emulator.getTexts().getValue("commands.succes.cmd_freeze.unfrozen"),
                            RoomChatMessageBubbles.ALERT);
                    gameClient
                            .getHabbo()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_freeze.user_unfrozen")
                                            .replace("%user%", params[1]),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }
            }
        } else {
            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_freeze.not_found")
                                    .replace("%user%", ""),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
