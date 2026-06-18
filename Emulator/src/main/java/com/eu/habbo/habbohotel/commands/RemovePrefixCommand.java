package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserPrefix;
import com.eu.habbo.messages.outgoing.inventory.prefixes.UserPrefixesComposer;

import java.util.List;

public class RemovePrefixCommand extends Command {
    public RemovePrefixCommand() {
        super("cmd_remove_prefix", Emulator.getTexts().getValue("commands.keys.cmd_remove_prefix").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 3) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_remove_prefix.usage"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetName = params[1];
        String prefixIdStr = params[2];

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetName);

        if (target == null) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_remove_prefix.user_not_found"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!CommandTargetGuard.canTarget(gameClient.getHabbo(), target)) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (prefixIdStr.equalsIgnoreCase("all")) {
            List<UserPrefix> prefixes = target.getInventory().getPrefixesComponent().getPrefixes();
            for (UserPrefix prefix : prefixes) {
                prefix.needsDelete(true);
                Emulator.getThreading().run(prefix);
            }
            // Clear in-memory
            for (UserPrefix prefix : prefixes) {
                target.getInventory().getPrefixesComponent().removePrefix(prefix);
            }

            target.getClient().sendResponse(new UserPrefixesComposer(target));

            gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.succes.cmd_remove_prefix.all").replace("%user%", targetName),
                RoomChatMessageBubbles.ALERT
            );
        } else {
            int prefixId;
            try {
                prefixId = Integer.parseInt(prefixIdStr);
            } catch (NumberFormatException e) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_remove_prefix.invalid_id"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            UserPrefix prefix = target.getInventory().getPrefixesComponent().getPrefix(prefixId);

            if (prefix == null) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_remove_prefix.not_found"), RoomChatMessageBubbles.ALERT);
                return true;
            }

            target.getInventory().getPrefixesComponent().removePrefix(prefix);
            prefix.needsDelete(true);
            Emulator.getThreading().run(prefix);

            target.getClient().sendResponse(new UserPrefixesComposer(target));

            gameClient.getHabbo().whisper(
                Emulator.getTexts().getValue("commands.succes.cmd_remove_prefix").replace("%user%", targetName).replace("%id%", String.valueOf(prefixId)),
                RoomChatMessageBubbles.ALERT
            );
        }

        return true;
    }
}
