package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;

public class MoonwalkCommand extends Command {
    public MoonwalkCommand() {
        super(
                "cmd_moonwalk",
                Emulator.getTexts().getValue("commands.keys.cmd_moonwalk").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() != null
                && gameClient.getUser().getUserStats().hasActiveClub()) {
            int effect = 136;
            if (gameClient.getUser().getRoomUnit().getEffectId() == 136) effect = 0;

            gameClient.getUser().getUserInfo().getCurrentRoom().giveEffect(gameClient.getUser(), effect, -1);

            return true;
        }

        return false;
    }
}
