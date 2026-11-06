package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class LayCommand extends Command {
    public LayCommand() {
        super(null, Emulator.getTexts().getValue("commands.keys.cmd_lay").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getRoomUnit() == null
                || !gameClient.getUser().getRoomUnit().canForcePosture()) return true;

        gameClient.getUser().getRoomUnit().cmdLay = true;
        gameClient.getUser().getUserInfo().getCurrentRoom().updateUser(gameClient.getUser());
        gameClient.getUser().getRoomUnit().cmdSit = true;
        gameClient
                .getUser()
                .getRoomUnit()
                .setBodyRotation(
                        RoomUserRotation.values()[
                                gameClient
                                                .getUser()
                                                .getRoomUnit()
                                                .getBodyRotation()
                                                .getValue()
                                        - gameClient
                                                        .getUser()
                                                        .getRoomUnit()
                                                        .getBodyRotation()
                                                        .getValue()
                                                % 2]);

        RoomTile tile = gameClient.getUser().getRoomUnit().getCurrentLocation();
        if (tile == null) {
            return false;
        }

        for (int i = 0; i < 3; i++) {
            RoomTile t = gameClient
                    .getUser()
                    .getUserInfo()
                    .getCurrentRoom()
                    .getLayout()
                    .getTileInFront(
                            tile,
                            gameClient.getUser().getRoomUnit().getBodyRotation().getValue(),
                            i);
            if (t == null || !t.isWalkable()) {
                return false;
            }
        }

        gameClient.getUser().getRoomUnit().setStatus(RoomUnitStatus.LAY, 0.5 + "");
        gameClient
                .getUser()
                .getUserInfo()
                .getCurrentRoom()
                .sendComposer(new RoomUserStatusComposer(gameClient.getUser().getRoomUnit()).compose());
        return true;
    }
}
