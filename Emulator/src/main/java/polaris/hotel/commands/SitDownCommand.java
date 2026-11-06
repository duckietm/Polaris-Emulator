package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;

public class SitDownCommand extends Command {
    public SitDownCommand() {
        super(
                "cmd_sitdown",
                Emulator.getTexts().getValue("commands.keys.cmd_sitdown").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        for (User user : gameClient.getUser().getUserInfo().getCurrentRoom().getUsers()) {
            if (user.getRoomUnit().isWalking()) {
                user.getRoomUnit().stopWalking();
            } else if (user.getRoomUnit().hasStatus(RoomUnitStatus.SIT)) {
                continue;
            }

            gameClient.getUser().getUserInfo().getCurrentRoom().makeSit(user);
        }

        return true;
    }
}
