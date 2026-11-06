package polaris.plugin.events.games;

import polaris.hotel.games.Game;
import polaris.hotel.users.User;

public class GameUserLeaveEvent extends GameUserEvent {

    public GameUserLeaveEvent(Game game, User user) {
        super(game, user);
    }
}
