package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class UpdateConfigCommand extends Command {
    public UpdateConfigCommand() {
        super(
                "cmd_update_config",
                Emulator.getTexts().getValue("commands.keys.cmd_update_config").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        reload();

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_config"),
                        RoomChatMessageBubbles.ALERT);

        return true;
    }

    /** The reload itself, without the whisper: also run by the RCON reloadcontent command. */
    public static void reload() {
        Emulator.getConfig().reload();
    }
}
