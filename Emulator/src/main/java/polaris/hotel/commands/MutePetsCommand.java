package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class MutePetsCommand extends Command {
    public MutePetsCommand() {
        super(null, Emulator.getTexts().getValue("commands.keys.cmd_mute_pets").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        gameClient.getUser().getUserStats().ignorePets = !gameClient.getUser().getUserStats().ignorePets;
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts()
                                .getValue("commands.succes.cmd_mute_pets."
                                        + (gameClient.getUser().getUserStats().ignorePets ? "ignored" : "unignored")),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
