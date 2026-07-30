package com.eu.habbo.messages.incoming.gamecenter;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterAchievementsConfigurationComposer;

public class GameCenterRequestGamesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new GameCenterAchievementsConfigurationComposer());
        SnowWarManager.getInstance().sendGamesInformation(this.client.getHabbo());
    }
}
