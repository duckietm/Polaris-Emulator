package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormLoadStageReadyEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        // AIR sends a trailing percentage (normally 100), while legacy Polaris
        // sends no payload. The isolated packet buffer safely discards either.

        int userId = this.client.getUser().getUserInfo().getId();
        SnowWarGame game = SnowWarManager.getInstance().getGameByUserId(userId);

        if (game == null) {
            return;
        }

        game.onLoadStageReady(userId);
    }
}
