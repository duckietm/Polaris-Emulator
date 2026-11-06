package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.messages.outgoing.rooms.users.RoomUserTalkComposer;

public class PushCommand extends Command {
    public PushCommand() {
        super("cmd_push", Emulator.getTexts().getValue("commands.keys.cmd_push").split(";"));
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
                                        .getValue("commands.error.cmd_push.not_found")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else if (user == gameClient.getUser()) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_push.push_self"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else {
                RoomTile tFront = gameClient
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

                if (tFront != null && tFront.isWalkable()) {
                    if (tFront.x == user.getRoomUnit().getX()
                            && tFront.y == user.getRoomUnit().getY()) {
                        RoomTile tFrontTarget = gameClient
                                .getUser()
                                .getUserInfo()
                                .getCurrentRoom()
                                .getLayout()
                                .getTileInFront(
                                        user.getRoomUnit().getCurrentLocation(),
                                        gameClient
                                                .getUser()
                                                .getRoomUnit()
                                                .getBodyRotation()
                                                .getValue());

                        if (tFrontTarget != null && tFrontTarget.isWalkable()) {
                            if (gameClient
                                            .getUser()
                                            .getUserInfo()
                                            .getCurrentRoom()
                                            .getLayout()
                                            .getDoorTile()
                                    == tFrontTarget) {
                                gameClient
                                        .getUser()
                                        .whisper(Emulator.getTexts()
                                                .getValue("commands.error.cmd_push.invalid")
                                                .replace("%username%", params[1]));
                                return true;
                            }
                            user.getRoomUnit().setGoalLocation(tFrontTarget);
                            gameClient
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .sendComposer(new RoomUserTalkComposer(new RoomChatMessage(
                                                    Emulator.getTexts()
                                                            .getValue("commands.succes.cmd_push.push")
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
                    } else {
                        gameClient
                                .getUser()
                                .whisper(
                                        Emulator.getTexts()
                                                .getValue("commands.error.cmd_push.cant_reach")
                                                .replace("%user%", params[1]),
                                        RoomChatMessageBubbles.ALERT);
                        return true;
                    }
                }
            }
            return true;
        }

        return true;
    }
}
