package polaris.messages.incoming.gamecenter;

import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.gamecenter.GameCenterDirectoryStatusComposer;

/**
 * The hub asks for the state of the game directory every time it opens: the leave block still
 * running (seconds) and the games left. The games a single player has finished are not counted.
 */
public class GameCenterCheckDirectoryStatusEvent extends MessageHandler {
    static final int GAMES_PLAYED_NOT_TRACKED = 0;

    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) return;

        int userId = this.client.getUser().getUserInfo().getId();
        SnowWarManager manager = SnowWarManager.getInstance();
        this.client.sendResponse(new GameCenterDirectoryStatusComposer(
                GameCenterDirectoryStatusComposer.STATUS_OK,
                manager.getBlockSecondsLeft(userId),
                GAMES_PLAYED_NOT_TRACKED,
                manager.getGamesLeft(userId)));
    }
}
