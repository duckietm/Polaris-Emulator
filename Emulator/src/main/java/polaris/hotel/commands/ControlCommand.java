package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class ControlCommand extends Command {
    public ControlCommand() {
        super(
                "cmd_control",
                Emulator.getTexts().getValue("commands.keys.cmd_control").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
            if (params.length == 2) {
                User target =
                        gameClient.getUser().getUserInfo().getCurrentRoom().getUser(params[1]);

                if (target == null) {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.error.cmd_control.not_found")
                                            .replace("%user%", params[1]),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }

                if (target == gameClient.getUser()) {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts().getValue("commands.error.cmd_control.not_self"),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }

                if (CommandTargetGuard.refuse(gameClient.getUser(), target)) {
                    return true;
                }

                User oldUser =
                        (User) gameClient.getUser().getRoomUnit().getCacheable().remove("control");

                if (oldUser != null) {
                    oldUser.getRoomUnit().getCacheable().remove("controller");
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_control.stopped")
                                            .replace(
                                                    "%user%",
                                                    oldUser.getUserInfo().getUsername()),
                                    RoomChatMessageBubbles.ALERT);
                }
                gameClient.getUser().getRoomUnit().getCacheable().put("control", target);
                target.getRoomUnit().getCacheable().put("controller", gameClient.getUser());
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.succes.cmd_control.controlling")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else {
                Object user = gameClient.getUser().getRoomUnit().getCacheable().get("control");

                if (user != null) {
                    gameClient.getUser().getRoomUnit().getCacheable().remove("control");

                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue("commands.succes.cmd_control.stopped")
                                            .replace(
                                                    "%user%",
                                                    ((User) user).getUserInfo().getUsername()),
                                    RoomChatMessageBubbles.ALERT);
                }
                return true;
            }
        }

        return true;
    }
}
