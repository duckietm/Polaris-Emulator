package com.eu.habbo.messages.incoming.gamecenter;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterDirectoryStatusComposer;

/**
 * The hub asks for the state of the game directory every time it opens. Our hotel keeps no block on
 * starting a game and does not count the games a single player has finished, so only the free games
 * left carry a real value; the rest says "everything is playable".
 */
public class GameCenterCheckDirectoryStatusEvent extends MessageHandler {
    static final int NOT_BLOCKED = 0;

    static final int GAMES_PLAYED_NOT_TRACKED = 0;

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) return;

        this.client.sendResponse(new GameCenterDirectoryStatusComposer(
                GameCenterDirectoryStatusComposer.STATUS_OK,
                NOT_BLOCKED,
                GAMES_PLAYED_NOT_TRACKED,
                SnowWarManager.getInstance()
                        .getGamesLeft(this.client.getHabbo().getHabboInfo().getId())));
    }
}
