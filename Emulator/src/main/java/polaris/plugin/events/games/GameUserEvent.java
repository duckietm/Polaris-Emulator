package polaris.plugin.events.games;

import polaris.hotel.games.Game;
import polaris.hotel.users.User;

public abstract class GameUserEvent extends GameEvent {

    public final User user;

    public GameUserEvent(Game game, User user) {
        super(game);

        this.user = user;
    }
}
