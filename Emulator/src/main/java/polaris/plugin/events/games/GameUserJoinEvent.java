package polaris.plugin.events.games;

import polaris.hotel.games.Game;
import polaris.hotel.users.User;

public class GameUserJoinEvent extends GameUserEvent {

    public GameUserJoinEvent(Game game, User user) {
        super(game, user);
    }
}
