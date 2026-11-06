package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.outgoing.generic.alerts.GenericAlertComposer;

public class RoomKickCommand extends Command {
    public RoomKickCommand() {
        super(
                "cmd_kickall",
                Emulator.getTexts().getValue("commands.keys.cmd_kickall").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        final Room room = gameClient.getUser().getUserInfo().getCurrentRoom();
        if (room != null) {
            if (params.length > 1) {
                StringBuilder message = new StringBuilder();
                for (int i = 1; i < params.length; i++) {
                    message.append(params[i]).append(" ");
                }
                room.sendComposer(new GenericAlertComposer(message + "\r\n-"
                                + gameClient.getUser().getUserInfo().getUsername())
                        .compose());
            }

            for (User user : room.getUsers()) {
                if (!(user.hasPermission(Permission.ACC_UNKICKABLE)
                        || user.hasPermission(Permission.ACC_SUPPORTTOOL)
                        || room.isOwner(user))) {
                    room.kickUser(user, true);
                }
            }
        }
        return true;
    }
}
