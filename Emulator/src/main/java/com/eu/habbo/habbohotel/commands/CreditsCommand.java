package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;

public class CreditsCommand extends Command {
    public CreditsCommand() {
        super(
                "cmd_credits",
                Emulator.getTexts().getValue("commands.keys.cmd_credits").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 3) {
            HabboInfo info = HabboManager.getOfflineHabboInfo(params[1]);

            if (info != null) {
                Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(params[1]);

                int credits;
                try {
                    credits = Integer.parseInt(params[2]);
                } catch (NumberFormatException e) {
                    gameClient
                            .getHabbo()
                            .whisper(
                                    Emulator.getTexts().getValue("commands.error.cmd_credits.invalid_amount"),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }
                if (habbo != null) {
                    if (credits != 0) {
                        habbo.giveCredits(credits);
                        if (habbo.getHabboInfo().getCurrentRoom() != null)
                            habbo.whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.generic.cmd_credits.received")
                                            .replace("%amount%", Integer.parseInt(params[2]) + ""),
                                    RoomChatMessageBubbles.ALERT);
                        else
                            habbo.alert(Emulator.getTexts()
                                    .getValue("commands.generic.cmd_credits.received")
                                    .replace("%amount%", Integer.parseInt(params[2]) + ""));

                        gameClient
                                .getHabbo()
                                .whisper(
                                        Emulator.getTexts()
                                                .getValue("commands.succes.cmd_credits.send")
                                                .replace("%amount%", Integer.parseInt(params[2]) + "")
                                                .replace("%user%", params[1]),
                                        RoomChatMessageBubbles.ALERT);

                    } else {
                        gameClient
                                .getHabbo()
                                .whisper(
                                        Emulator.getTexts().getValue("commands.error.cmd_credits.invalid_amount"),
                                        RoomChatMessageBubbles.ALERT);
                    }
                } else {
                    Emulator.getGameEnvironment().getHabboManager().giveCredits(info.getId(), credits);
                    gameClient
                            .getHabbo()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_credits.send")
                                            .replace("%amount%", Integer.parseInt(params[2]) + "")
                                            .replace("%user%", params[1]),
                                    RoomChatMessageBubbles.ALERT);
                }
            } else {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_credits.user_not_found")
                                        .replace("%amount%", params[2])
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
            }
        } else {
            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_credits.invalid_amount"),
                            RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}
