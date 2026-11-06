package polaris.messages.incoming.gamecenter;

import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.gamecenter.GameCenterAchievementsConfigurationComposer;

public class GameCenterRequestGamesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new GameCenterAchievementsConfigurationComposer());
        SnowWarManager.getInstance().sendGamesInformation(this.client.getUser());
    }
}
