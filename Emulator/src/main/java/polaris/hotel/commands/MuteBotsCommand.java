package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class MuteBotsCommand extends Command {
    public MuteBotsCommand() {
        super(null, Emulator.getTexts().getValue("commands.keys.cmd_mute_bots").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        gameClient.getUser().getUserStats().ignoreBots = !gameClient.getUser().getUserStats().ignoreBots;
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts()
                                .getValue("commands.succes.cmd_mute_bots."
                                        + (gameClient.getUser().getUserStats().ignoreBots ? "ignored" : "unignored")),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
