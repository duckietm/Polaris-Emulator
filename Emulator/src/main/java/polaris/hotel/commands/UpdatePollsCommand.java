package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class UpdatePollsCommand extends Command {
    public UpdatePollsCommand() {
        super(
                "cmd_update_polls",
                Emulator.getTexts().getValue("commands.keys.cmd_update_polls").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Emulator.getGameEnvironment().getPollManager().loadPolls();
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_polls"), RoomChatMessageBubbles.ALERT);
        return true;
    }
}
