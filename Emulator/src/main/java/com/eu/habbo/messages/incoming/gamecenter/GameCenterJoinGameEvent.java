package com.eu.habbo.messages.incoming.gamecenter;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarConstants;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterAchievementsConfigurationComposer;
import com.eu.habbo.messages.outgoing.gamecenter.basejump.BaseJumpJoinQueueComposer;
import com.eu.habbo.messages.outgoing.gamecenter.basejump.BaseJumpLoadGameComposer;
import com.eu.habbo.messages.outgoing.gamecenter.basejump.BaseJumpLoadGameURLComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGenericErrorComposer;

public class GameCenterJoinGameEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int gameId = this.packet.readInt();

        if (gameId == 0) //SnowStorm
        {
            if (Emulator.getConfig().getBoolean("gamecenter.snowwar.enabled", true)) {
                SnowWarManager.getInstance().joinQueue(this.client.getHabbo());
            } else {
                this.client.sendResponse(new SnowStormGenericErrorComposer(SnowWarConstants.ERROR_INTERNAL));
            }
        } else if (gameId == 3) //BaseJump
        {
            this.client.sendResponse(new GameCenterAchievementsConfigurationComposer());
            this.client.sendResponse(new BaseJumpLoadGameURLComposer());
            this.client.sendResponse(new BaseJumpLoadGameComposer(this.client, 3));
        } else if (gameId == 4) {
            this.client.sendResponse(new BaseJumpJoinQueueComposer(4));
            this.client.sendResponse(new BaseJumpLoadGameURLComposer());
        }
    }
}
