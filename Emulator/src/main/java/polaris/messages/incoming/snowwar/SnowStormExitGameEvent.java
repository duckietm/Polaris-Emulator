package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormExitGameEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        int userId = this.client.getUser().getUserInfo().getId();
        SnowWarGame game = SnowWarManager.getInstance().getGameByUserId(userId);

        if (game != null) {
            game.exitGame(userId);
        } else {
            SnowWarManager.getInstance().leaveQueue(this.client.getUser());
        }
    }
}
