package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class UpdateCalendarCommand extends Command {

    public UpdateCalendarCommand() {
        super(
                "cmd_update_calendar",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_update_calendar")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        reload();
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.success.cmd_update_calendar"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }

    /** The reload itself, without the whisper: also run by the RCON reloadcontent command. */
    public static void reload() {
        Emulator.getGameEnvironment().getCalendarManager().reload();
    }
}
