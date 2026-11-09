package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class UpdatePetDataCommand extends Command {
    public UpdatePetDataCommand() {
        super(
                "cmd_update_pet_data",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_update_pet_data")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        reload();

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_pet_data"),
                        RoomChatMessageBubbles.ALERT);

        return true;
    }

    /** The reload itself, without the whisper: also run by the RCON reloadcontent command. */
    public static void reload() {
        Emulator.getGameEnvironment().getPetManager().reloadPetData();
    }
}
