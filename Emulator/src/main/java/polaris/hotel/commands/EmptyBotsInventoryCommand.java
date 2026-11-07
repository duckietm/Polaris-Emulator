package polaris.hotel.commands;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.outgoing.inventory.InventoryBotsComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

public class EmptyBotsInventoryCommand extends Command {
    public EmptyBotsInventoryCommand() {
        super(
                "cmd_empty_bots",
                Emulator.getTexts().getValue("commands.keys.cmd_empty_bots").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1
                || (params.length >= 2 && !params[1].equals(Emulator.getTexts().getValue("generic.yes")))) {
            if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
                if (gameClient.getUser().getUserInfo().getCurrentRoom().getUserCount() > 10) {
                    gameClient
                            .getUser()
                            .alert(Emulator.getTexts()
                                    .getValue("commands.succes.cmd_empty_bots.verify")
                                    .replace(
                                            "%generic.yes%", Emulator.getTexts().getValue("generic.yes")));
                } else {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_empty_bots.verify")
                                            .replace(
                                                    "%generic.yes%",
                                                    Emulator.getTexts().getValue("generic.yes")),
                                    RoomChatMessageBubbles.ALERT);
                }
            }

            return true;
        }

        if (params.length >= 2 && params[1].equalsIgnoreCase(Emulator.getTexts().getValue("generic.yes"))) {

            User user = (params.length == 3 && gameClient.getUser().hasPermission(Permission.ACC_EMPTY_OTHERS))
                    ? Emulator.getGameEnvironment().getUserManager().getUser(params[2])
                    : gameClient.getUser();

            if (CommandTargetGuard.refuse(gameClient.getUser(), user)) {
                return true;
            }

            if (user != null) {
                List<Bot> bots = user.getInventory().getBotsComponent().takeAllBots();
                bots.forEach(
                        object -> Emulator.getGameEnvironment().getBotManager().deleteBot(object));

                user.getClient().sendResponse(new InventoryRefreshComposer());
                user.getClient().sendResponse(new InventoryBotsComposer(user));

                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.succes.cmd_empty_bots.cleared")
                                        .replace(
                                                "%username%", user.getUserInfo().getUsername()),
                                RoomChatMessageBubbles.ALERT);
            } else {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_empty_bots"),
                                RoomChatMessageBubbles.ALERT);
            }
        }

        return true;
    }
}
