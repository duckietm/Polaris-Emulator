package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;

public class UpdateHotelViewCommand extends Command {
    protected UpdateHotelViewCommand() {
        super(
                "cmd_update_hotel_view",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_update_hotel_view")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Emulator.getGameEnvironment().getHotelViewManager().getNewsList().reload();
        Emulator.getGameEnvironment().getHotelViewManager().getHallOfFame().reload();
        Emulator.getGameEnvironment().getHotelViewManager().reloadAndBroadcastLandingView();

        gameClient.getUser().whisper(Emulator.getTexts().getValue("commands.succes.cmd_update_hotel_view"));

        return true;
    }
}
