package polaris.hotel.commands;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserPrefix;

public class ListPrefixesCommand extends Command {
    public ListPrefixesCommand() {
        super(
                "cmd_list_prefixes",
                Emulator.getTexts().getValue("commands.keys.cmd_list_prefixes").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_list_prefixes.usage"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetName = params[1];

        User target = Emulator.getGameEnvironment().getUserManager().getUser(targetName);

        if (target == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_list_prefixes.user_not_found"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        List<UserPrefix> prefixes = target.getInventory().getPrefixesComponent().getPrefixes();

        if (prefixes.isEmpty()) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_list_prefixes.empty")
                                    .replace("%user%", targetName),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(Emulator.getTexts()
                        .getValue("commands.succes.cmd_list_prefixes.header")
                        .replace("%user%", targetName))
                .append("\r");

        for (UserPrefix prefix : prefixes) {
            sb.append("ID: ")
                    .append(prefix.getId())
                    .append(" | {")
                    .append(prefix.getText())
                    .append("}")
                    .append(" | Color: ")
                    .append(prefix.getColor())
                    .append(prefix.getIcon().isEmpty() ? "" : " | Icon: " + prefix.getIcon())
                    .append(prefix.getEffect().isEmpty() ? "" : " | Effect: " + prefix.getEffect())
                    .append(prefix.isActive() ? " [ACTIVE]" : "")
                    .append("\r");
        }

        gameClient.getUser().whisper(sb.toString(), RoomChatMessageBubbles.ALERT);

        return true;
    }
}
