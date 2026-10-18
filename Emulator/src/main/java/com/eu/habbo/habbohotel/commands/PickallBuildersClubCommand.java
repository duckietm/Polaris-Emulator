package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;

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
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) {
            return true;
        }

        if (room.isOwner(gameClient.getHabbo())
                || (room.hasGuild()
                        && room.getGuildRightLevel(gameClient.getHabbo())
                                .isEqualOrGreaterThan(RoomRightLevels.GUILD_ADMIN))) {
            room.getItemManager().removeBuildersClubItems(gameClient.getHabbo());
        }

        return true;
    }
}
