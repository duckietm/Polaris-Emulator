package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class UnloadRoomCommand extends Command {

    public UnloadRoomCommand() {
        super(
                "cmd_unload",
                Emulator.getTexts().getValue("commands.keys.cmd_unload").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

        if (room == null) {
            return true;
        }

        if (room.getOwnerId() == gameClient.getUser().getUserInfo().getId()
                || gameClient.getUser().hasPermission(Permission.ACC_UNLOAD_ANY_ROOM)) {
            room.dispose();
            return true;
        }

        gameClient.getUser().whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
        return true;
    }
}
