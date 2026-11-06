package polaris.hotel.commands;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomRightLevels;

/**
 * {@code :pickallbc}: takes every Builders Club furni out of the room. The AIR client sends the fixed
 * {@code :pickallbc} line after its own confirmation, so the key is not configurable; it shares the
 * {@code cmd_pickall} permission.
 */
public class PickallBuildersClubCommand extends Command {
    public PickallBuildersClubCommand() {
        super("cmd_pickall", new String[] {"pickallbc"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

        if (room == null) {
            return true;
        }

        if (room.isOwner(gameClient.getUser())
                || (room.hasGuild()
                        && room.getGuildRightLevel(gameClient.getUser())
                                .isEqualOrGreaterThan(RoomRightLevels.GUILD_ADMIN))) {
            room.getItemManager().removeBuildersClubItems(gameClient.getUser());
        }

        return true;
    }
}
