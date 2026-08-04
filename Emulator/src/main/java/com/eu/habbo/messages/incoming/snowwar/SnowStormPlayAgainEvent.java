package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarGame;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarGamePlayer;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class SnowStormPlayAgainEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }

        int userId = this.client.getHabbo().getHabboInfo().getId();
        SnowWarGame game = SnowWarManager.getInstance().getGameByUserId(userId);

        // After the restart window the ended game is cleaned up and the user no
        // longer maps to it; treat "play again" as a plain fresh queue join so
        // the button never goes dead on the results screen.
        if (game == null) {
            SnowWarManager.getInstance().joinQueue(this.client.getHabbo());
            return;
        }

        SnowWarGamePlayer player = game.getPlayer(userId);
        if (player == null) {
            SnowWarManager.getInstance().joinQueue(this.client.getHabbo());
            return;
        }

        game.handlePlayAgain(player);
    }
}
