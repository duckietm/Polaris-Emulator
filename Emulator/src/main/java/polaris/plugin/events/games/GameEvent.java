package polaris.plugin.events.games;

import polaris.hotel.games.Game;
import polaris.plugin.Event;

public abstract class GameEvent extends Event {

    public final Game game;

    public GameEvent(Game game) {
        this.game = game;
    }
}
