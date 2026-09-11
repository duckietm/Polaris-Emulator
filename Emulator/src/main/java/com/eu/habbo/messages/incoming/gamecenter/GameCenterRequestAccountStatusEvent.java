package com.eu.habbo.messages.incoming.gamecenter;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterAccountInfoComposer;

public class GameCenterRequestAccountStatusEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int gameId = this.packet.readInt();

        if (this.client.getHabbo() == null) {
            return;
        }

        // AIR games_main footer: the free games left, plus the extra games the
        // player bought with tokens; -1 means unlimited and hides the counter.
        this.client.sendResponse(new GameCenterAccountInfoComposer(
                gameId,
                SnowWarManager.getInstance()
                        .getGamesLeft(this.client.getHabbo().getHabboInfo().getId())));
    }
}
