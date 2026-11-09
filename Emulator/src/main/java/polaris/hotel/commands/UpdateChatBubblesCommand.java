package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class UpdateChatBubblesCommand extends Command {

    public UpdateChatBubblesCommand() {
        super(
                "cmd_update_chat_bubbles",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_update_chat_bubbles")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        reload();
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.success.cmd_update_chat_bubbles"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }

    /** The reload itself, without the whisper: also run by the RCON reloadcontent command. */
    public static void reload() {
        RoomChatMessageBubbles.removeDynamicBubbles();
        Emulator.getGameEnvironment().getRoomChatBubbleManager().reload();
    }
}
