package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;

public class PickallCommand extends Command {
    public PickallCommand() {
        super(
                "cmd_pickall",
                Emulator.getTexts().getValue("commands.keys.cmd_pickall").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            if (room.isOwner(gameClient.getUser())) {
                // Builders Club furni stay: as in Habbo, only :pickallbc takes them out.
                room.getItemManager().ejectAllExceptBuildersClub();
                return true;
            }

            room.ejectUserFurni(gameClient.getUser().getUserInfo().getId());
        }

        return true;
    }
}
