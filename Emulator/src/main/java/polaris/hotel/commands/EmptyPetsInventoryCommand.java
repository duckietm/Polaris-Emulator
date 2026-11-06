package polaris.hotel.commands;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.outgoing.inventory.InventoryPetsComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

public class EmptyPetsInventoryCommand extends Command {
    public EmptyPetsInventoryCommand() {
        super(
                "cmd_empty_pets",
                Emulator.getTexts().getValue("commands.keys.cmd_empty_pets").split(";"));
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
                                    .getValue("commands.succes.cmd_empty_pets.verify")
                                    .replace(
                                            "%generic.yes%", Emulator.getTexts().getValue("generic.yes")));
                } else {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_empty_pets.verify")
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
                List<Pet> pets = new ArrayList<>(
                        user.getInventory().getPetsComponent().getPets().values());
                user.getInventory().getPetsComponent().getPets().clear();
                pets.forEach(
                        object -> Emulator.getGameEnvironment().getPetManager().deletePet(object));

                user.getClient().sendResponse(new InventoryRefreshComposer());
                user.getClient().sendResponse(new InventoryPetsComposer(user));

                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.succes.cmd_empty_pets.cleared")
                                        .replace(
                                                "%username%", user.getUserInfo().getUsername()),
                                RoomChatMessageBubbles.ALERT);
            } else {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_empty_pets"),
                                RoomChatMessageBubbles.ALERT);
            }
        }

        return true;
    }
}
