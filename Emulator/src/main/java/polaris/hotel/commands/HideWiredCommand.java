package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;

public class HideWiredCommand extends Command {
    public HideWiredCommand() {
        super(
                "cmd_hidewired",
                Emulator.getTexts().getValue("commands.keys.cmd_hidewired").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            if (room.isOwner(gameClient.getUser())) {
                room.setHideWired(!room.isHideWired());
                gameClient
                        .getUser()
                        .whisper(Emulator.getTexts()
                                .getValue(
                                        "commands.succes.cmd_hidewired." + (room.isHideWired() ? "hidden" : "shown")));
            } else {
                gameClient.getUser().whisper(Emulator.getTexts().getValue("commands.errors.cmd_hidewired.permission"));
            }
        }

        return true;
    }
}
