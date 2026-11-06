package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnitStatus;

public class CoordsCommand extends Command {

    public CoordsCommand() {
        super(
                "cmd_coords",
                Emulator.getTexts().getValue("commands.keys.cmd_coords").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getRoomUnit() == null
                || gameClient.getUser().getUserInfo().getCurrentRoom() == null) return false;

        if (params.length == 1) {
            gameClient
                    .getUser()
                    .alert(Emulator.getTexts().getValue("commands.generic.cmd_coords.title") + "\r\n" + "x: "
                            + gameClient.getUser().getRoomUnit().getX() + "\r" + "y: "
                            + gameClient.getUser().getRoomUnit().getY() + "\r" + "z: "
                            + (gameClient.getUser().getRoomUnit().hasStatus(RoomUnitStatus.SIT)
                                    ? gameClient.getUser().getRoomUnit().getStatus(RoomUnitStatus.SIT)
                                    : gameClient.getUser().getRoomUnit().getZ())
                            + "\r" + Emulator.getTexts().getValue("generic.rotation.head")
                            + ": " + gameClient.getUser().getRoomUnit().getHeadRotation() + "-"
                            + gameClient
                                    .getUser()
                                    .getRoomUnit()
                                    .getHeadRotation()
                                    .getValue() + "\r" + Emulator.getTexts().getValue("generic.rotation.body")
                            + ": " + gameClient.getUser().getRoomUnit().getBodyRotation() + "-"
                            + gameClient
                                    .getUser()
                                    .getRoomUnit()
                                    .getBodyRotation()
                                    .getValue() + "\r" + Emulator.getTexts().getValue("generic.sitting")
                            + ": "
                            + (gameClient.getUser().getRoomUnit().hasStatus(RoomUnitStatus.SIT)
                                    ? Emulator.getTexts().getValue("generic.yes")
                                    : Emulator.getTexts().getValue("generic.no"))
                            + "\r" + "Tile State: "
                            + gameClient
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .getLayout()
                                    .getTile(
                                            gameClient.getUser().getRoomUnit().getX(),
                                            gameClient.getUser().getRoomUnit().getY())
                                    .state
                                    .name()
                            + "\r" + "Tile Walkable: "
                            + gameClient
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .getLayout()
                                    .getTile(
                                            gameClient.getUser().getRoomUnit().getX(),
                                            gameClient.getUser().getRoomUnit().getY())
                                    .isWalkable()
                            + "\r" + "Tile relative height: "
                            + gameClient
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .getLayout()
                                    .getTile(
                                            gameClient.getUser().getRoomUnit().getX(),
                                            gameClient.getUser().getRoomUnit().getY())
                                    .relativeHeight()
                            + "\r" + "Tile stack height: "
                            + gameClient
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .getLayout()
                                    .getTile(
                                            gameClient.getUser().getRoomUnit().getX(),
                                            gameClient.getUser().getRoomUnit().getY())
                                    .getStackHeight());

        } else {
            RoomTile tile = gameClient
                    .getUser()
                    .getUserInfo()
                    .getCurrentRoom()
                    .getLayout()
                    .getTile(Short.parseShort(params[1]), Short.parseShort(params[2]));

            if (tile != null) {
                gameClient
                        .getUser()
                        .alert(Emulator.getTexts().getValue("commands.generic.cmd_coords.title") + "\r\n" + "x: "
                                + tile.x + "\r" + "y: "
                                + tile.y + "\r" + "z: "
                                + tile.z + "\r" + "Tile State: "
                                + tile.state.name() + "\r" + "Tile Relative Height: "
                                + tile.relativeHeight() + "\r" + "Tile Stack Height: "
                                + tile.getStackHeight() + "\r" + "Tile Walkable: "
                                + (tile.isWalkable() ? "Yes" : "No") + "\r");
            } else {
                gameClient.getUser().whisper(Emulator.getTexts().getValue("generic.tile.not.exists"));
            }
        }
        return true;
    }
}
