package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class TeleportCommand extends Command {
    public TeleportCommand() {
        super(
                "cmd_teleport",
                Emulator.getTexts().getValue("commands.keys.cmd_teleport").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getRiding() == null)
            if (gameClient.getUser().getRoomUnit().cmdTeleport) {
                gameClient.getUser().getRoomUnit().cmdTeleport = false;
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.succes.cmd_teleport.disabled"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else {
                gameClient.getUser().getRoomUnit().cmdTeleport = true;
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.succes.cmd_teleport.enabled"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        return true;
    }
}
