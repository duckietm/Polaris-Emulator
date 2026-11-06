package polaris.plugin.events.games;

import polaris.hotel.games.Game;

public class GameStartedEvent extends GameEvent {

    public GameStartedEvent(Game game) {
        super(game);
    }
}
