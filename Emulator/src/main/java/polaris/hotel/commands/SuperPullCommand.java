package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.messages.outgoing.rooms.users.RoomUserTalkComposer;

public class SuperPullCommand extends Command {
    public SuperPullCommand() {
        super(
                "cmd_superpull",
                Emulator.getTexts().getValue("commands.keys.cmd_superpull").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            User user = gameClient.getUser().getUserInfo().getCurrentRoom().getUser(params[1]);

            if (user == null) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_pull.not_found")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else if (user == gameClient.getUser()) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_pull.pull_self"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else {
                RoomTile tile = gameClient
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .getLayout()
                        .getTileInFront(
                                gameClient.getUser().getRoomUnit().getCurrentLocation(),
                                gameClient
                                        .getUser()
                                        .getRoomUnit()
                                        .getBodyRotation()
                                        .getValue());

                if (tile != null && tile.isWalkable()) {
                    if (tile
                            == gameClient
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .getLayout()
                                    .getDoorTile()) {
                        gameClient
                                .getUser()
                                .whisper(
                                        Emulator.getTexts()
                                                .getValue("commands.error.cmd_pull.invalid")
                                                .replace("%username%", params[1]),
                                        RoomChatMessageBubbles.ALERT);
                        return true;
                    }
                    user.getRoomUnit().setGoalLocation(tile);
                    gameClient
                            .getUser()
                            .getUserInfo()
                            .getCurrentRoom()
                            .sendComposer(new RoomUserTalkComposer(new RoomChatMessage(
                                            Emulator.getTexts()
                                                    .getValue("commands.succes.cmd_pull.pull")
                                                    .replace("%user%", params[1])
                                                    .replace(
                                                            "%gender_name%",
                                                            (gameClient
                                                                            .getUser()
                                                                            .getUserInfo()
                                                                            .getGender()
                                                                            .equals(UserGender.M)
                                                                    ? Emulator.getTexts()
                                                                            .getValue("gender.him")
                                                                    : Emulator.getTexts()
                                                                            .getValue("gender.her"))),
                                            gameClient.getUser(),
                                            gameClient.getUser(),
                                            RoomChatMessageBubbles.NORMAL))
                                    .compose());
                }
            }
            return true;
        }

        return true;
    }
}
