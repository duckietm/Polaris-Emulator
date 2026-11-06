package polaris.plugin.events.games;

import polaris.hotel.games.Game;

public class GameStoppedEvent extends GameEvent {

    public GameStoppedEvent(Game game) {
        super(game);
    }
}
