package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomRightLevels;

public class EjectAllCommand extends Command {
    public EjectAllCommand() {
        super(
                "cmd_ejectall",
                Emulator.getTexts().getValue("commands.keys.cmd_ejectall").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            if (room.isOwner(gameClient.getUser())
                    || (room.hasGuild()
                            && room.getGuildRightLevel(gameClient.getUser()).equals(RoomRightLevels.GUILD_ADMIN))) {
                room.ejectAll(gameClient.getUser());
            }
        }

        return true;
    }
}
