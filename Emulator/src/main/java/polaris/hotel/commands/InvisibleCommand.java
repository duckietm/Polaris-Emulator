package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.outgoing.rooms.users.RoomUserRemoveComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.messages.outgoing.rooms.users.RoomUsersComposer;
import polaris.threading.runnables.RoomUnitTeleport;

public class InvisibleCommand extends Command {
    public InvisibleCommand() {
        super(
                "cmd_invisible",
                Emulator.getTexts().getValue("commands.keys.cmd_invisible").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        RoomUnit roomUnit = gameClient.getUser().getRoomUnit();

        if (roomUnit.isInvisible()) {
            RoomLayout roomLayout = roomUnit.getRoom().getLayout();

            new RoomUnitTeleport(
                            roomUnit,
                            roomUnit.getRoom(),
                            roomLayout.getDoorTile().x,
                            roomLayout.getDoorTile().y,
                            roomLayout.getDoorTile().z,
                            0)
                    .run();

            roomUnit.setInvisible(false);
            roomUnit.setInRoom(true);

            roomUnit.getRoom().sendComposer(new RoomUsersComposer(gameClient.getUser()).compose());
            roomUnit.getRoom().sendComposer(new RoomUserStatusComposer(roomUnit).compose());

            WiredManager.triggerUserEntersRoom(roomUnit.getRoom(), roomUnit);
            roomUnit.getRoom().userEntered(gameClient.getUser());

            gameClient.getUser().whisper(Emulator.getTexts().getValue("commands.succes.cmd_invisible.updated.back"));

            return true;
        }

        roomUnit.setInvisible(true);
        gameClient.getUser().whisper(Emulator.getTexts().getValue("commands.succes.cmd_invisible.updated"));
        gameClient
                .getUser()
                .getUserInfo()
                .getCurrentRoom()
                .sendComposer(new RoomUserRemoveComposer(roomUnit).compose());

        return true;
    }
}
