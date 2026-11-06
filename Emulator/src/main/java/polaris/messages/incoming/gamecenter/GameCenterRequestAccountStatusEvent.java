package polaris.messages.incoming.gamecenter;

import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.gamecenter.GameCenterAccountInfoComposer;

public class GameCenterRequestAccountStatusEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int gameId = this.packet.readInt();

        if (this.client.getUser() == null) {
            return;
        }

        // AIR games_main footer: the free games left, plus the extra games the
        // player bought with tokens; -1 means unlimited and hides the counter.
        this.client.sendResponse(new GameCenterAccountInfoComposer(
                gameId,
                SnowWarManager.getInstance()
                        .getGamesLeft(this.client.getUser().getUserInfo().getId())));
    }
}
