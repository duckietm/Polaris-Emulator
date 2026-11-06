package polaris.hotel.commands;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.inventory.InventoryItemsComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.threading.runnables.QueryDeleteUserItems;

public class EmptyInventoryCommand extends Command {
    public EmptyInventoryCommand() {
        super(
                "cmd_empty",
                Emulator.getTexts().getValue("commands.keys.cmd_empty").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1
                || (params.length == 2 && !params[1].equals(Emulator.getTexts().getValue("generic.yes")))) {
            if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
                if (gameClient.getUser().getUserInfo().getCurrentRoom().getUserCount() > 10) {
                    gameClient
                            .getUser()
                            .alert(Emulator.getTexts()
                                    .getValue("commands.succes.cmd_empty.verify")
                                    .replace(
                                            "%generic.yes%", Emulator.getTexts().getValue("generic.yes")));
                } else {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_empty.verify")
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

            // Emptying another user's inventory is a moderation action on them: never on a higher rank.
            if (CommandTargetGuard.refuse(gameClient.getUser(), user)) {
                return true;
            }

            if (user != null) {
                List<UserItem> items = new ArrayList<>(
                        user.getInventory().getItemsComponent().getItems().values());
                user.getInventory().getItemsComponent().getItems().clear();
                Emulator.getThreading().runPersistence(new QueryDeleteUserItems(items));

                user.getClient().sendResponse(new InventoryRefreshComposer());
                // The target's own (now empty) inventory: the moderator's used to be sent to them.
                user.getClient()
                        .sendResponse(new InventoryItemsComposer(
                                0, 1, user.getInventory().getItemsComponent().getItems()));

                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.succes.cmd_empty.cleared")
                                        .replace(
                                                "%username%", user.getUserInfo().getUsername()),
                                RoomChatMessageBubbles.ALERT);
            } else {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_empty"), RoomChatMessageBubbles.ALERT);
            }
        }

        return true;
    }
}
