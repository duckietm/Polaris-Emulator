package polaris.messages.incoming.snowwar;

import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarGamePlayer;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;

public class SnowStormPlayAgainEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        int userId = this.client.getUser().getUserInfo().getId();
        SnowWarGame game = SnowWarManager.getInstance().getGameByUserId(userId);

        // After the restart window the ended game is cleaned up and the user no
        // longer maps to it; treat "play again" as a plain fresh queue join so
        // the button never goes dead on the results screen.
        if (game == null) {
            SnowWarManager.getInstance().joinQueue(this.client.getUser());
            return;
        }

        SnowWarGamePlayer player = game.getPlayer(userId);
        if (player == null) {
            SnowWarManager.getInstance().joinQueue(this.client.getUser());
            return;
        }

        game.handlePlayAgain(player);
    }
}
