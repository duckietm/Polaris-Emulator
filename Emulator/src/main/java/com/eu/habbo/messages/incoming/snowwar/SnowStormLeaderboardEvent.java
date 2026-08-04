package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarLeaderboardRepository;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormLeaderboardComposer;

/** Shared decoder for the four AIR SnowStorm leaderboard requests. */
abstract class SnowStormLeaderboardEvent extends MessageHandler {

    protected abstract boolean weekly();

    protected abstract boolean friendsOnly();

    protected abstract int responseHeader();

    protected final void handleLeaderboardRequest(
            int gameTypeId, int weekOffset, int startRank, int viewSize, int windowSize) {
        if (this.client.getHabbo() == null) {
            return;
        }
        if (gameTypeId != 0) {
            return;
        }

        int userId = this.client.getHabbo().getHabboInfo().getId();
        SnowWarManager manager = SnowWarManager.getInstance();
        if (!manager.allowPacket(userId)) {
            return;
        }

        SnowWarLeaderboardRepository.Page page = manager.getLeaderboard(
                userId, this.weekly(), this.friendsOnly(), weekOffset, startRank, Math.max(viewSize, windowSize));
        this.client.sendResponse(new SnowStormLeaderboardComposer(this.responseHeader(), page));
    }
}
