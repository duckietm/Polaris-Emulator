package polaris.hotel.commands;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserPrefix;
import polaris.hotel.users.inventory.PrefixUpdates;

public class RemovePrefixCommand extends Command {
    public RemovePrefixCommand() {
        super(
                "cmd_remove_prefix",
                Emulator.getTexts().getValue("commands.keys.cmd_remove_prefix").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 3) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_remove_prefix.usage"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetName = params[1];
        String prefixIdStr = params[2];

        User target = Emulator.getGameEnvironment().getUserManager().getUser(targetName);

        if (target == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_remove_prefix.user_not_found"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!CommandTargetGuard.canTarget(gameClient.getUser(), target)) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (prefixIdStr.equalsIgnoreCase("all")) {
            List<UserPrefix> prefixes =
                    target.getInventory().getPrefixesComponent().getPrefixes();
            boolean activeRemoved = false;
            for (UserPrefix prefix : prefixes) {
                activeRemoved |= target.getInventory().getPrefixesComponent().deletePrefix(prefix);
            }

            PrefixUpdates.removed(target, activeRemoved);

            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_remove_prefix.all")
                                    .replace("%user%", targetName),
                            RoomChatMessageBubbles.ALERT);
        } else {
            int prefixId;
            try {
                prefixId = Integer.parseInt(prefixIdStr);
            } catch (NumberFormatException e) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_remove_prefix.invalid_id"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            UserPrefix prefix = target.getInventory().getPrefixesComponent().getPrefix(prefixId);

            if (prefix == null) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_remove_prefix.not_found"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            boolean activeRemoved = target.getInventory().getPrefixesComponent().deletePrefix(prefix);
            PrefixUpdates.removed(target, activeRemoved);

            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_remove_prefix")
                                    .replace("%user%", targetName)
                                    .replace("%id%", String.valueOf(prefixId)),
                            RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
