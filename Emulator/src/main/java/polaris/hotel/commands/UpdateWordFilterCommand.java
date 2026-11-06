package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class UpdateWordFilterCommand extends Command {
    public UpdateWordFilterCommand() {
        super(
                "cmd_update_wordfilter",
                Emulator.getTexts().getValue("commands.keys.update_wordfilter").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Emulator.getGameEnvironment().getWordFilter().reload();

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_wordfilter"),
                        RoomChatMessageBubbles.ALERT);

        return true;
    }
}
