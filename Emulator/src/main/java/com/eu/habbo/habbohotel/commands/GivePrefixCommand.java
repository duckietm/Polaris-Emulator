package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserPrefix;
import com.eu.habbo.messages.outgoing.inventory.prefixes.PrefixReceivedComposer;
import com.eu.habbo.messages.outgoing.inventory.prefixes.UserPrefixesComposer;

public class GivePrefixCommand extends Command {
    public GivePrefixCommand() {
        super("cmd_give_prefix", Emulator.getTexts().getValue("commands.keys.cmd_give_prefix").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 4) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_give_prefix.usage"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetName = params[1];
        String text = params[2];
        String color = params[3];
        String icon = params.length > 4 ? params[4] : "";
        String effect = params.length > 5 ? params[5] : "";

        // Validate color
        String[] colorParts = color.split(",");
        for (String part : colorParts) {
            if (!part.matches("^#[0-9A-Fa-f]{6}$")) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_give_prefix.invalid_color"), RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        if (text.length() > 15) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_give_prefix.too_long"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetName);

        if (target == null) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_give_prefix.user_not_found"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), target)) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        UserPrefix prefix = new UserPrefix(target.getHabboInfo().getId(), text, color, icon, effect);
        prefix.run();
        target.getInventory().getPrefixesComponent().addPrefix(prefix);

        target.getClient().sendResponse(new PrefixReceivedComposer(prefix));
        target.getClient().sendResponse(new UserPrefixesComposer(target));

        gameClient.getHabbo().whisper(
            Emulator.getTexts().getValue("commands.succes.cmd_give_prefix")
                .replace("%user%", targetName)
                .replace("%prefix%", text),
            RoomChatMessageBubbles.ALERT
        );

        return true;
    }
}
