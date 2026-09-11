package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarLeaderboardRepository;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormAllTimeLeaderboardComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGroupLeaderboardComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormLeaderboardComposer;

/** Shared decoder for the six AIR SnowStorm leaderboard requests. */
abstract class SnowStormLeaderboardEvent extends MessageHandler {

    protected abstract boolean weekly();

    protected abstract boolean friendsOnly();

    protected abstract int responseHeader();

    /** Group tables rank guilds; AIR appends the viewer's favourite guild id. */
    protected boolean groups() {
        return false;
    }

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

        int limit = Math.max(viewSize, windowSize);
        if (this.groups()) {
            SnowWarLeaderboardRepository.GroupPage groupPage =
                    manager.getGroupLeaderboard(userId, this.weekly(), weekOffset, startRank, limit);
            this.client.sendResponse(
                    new SnowStormGroupLeaderboardComposer(this.responseHeader(), this.weekly(), groupPage));
            return;
        }

        SnowWarLeaderboardRepository.Page page =
                manager.getLeaderboard(userId, this.weekly(), this.friendsOnly(), weekOffset, startRank, limit);
        // AIR's all-time tables use Game2LeaderboardParser, which has no week
        // header; only the weekly views carry the year/week/offset prefix.
        this.client.sendResponse(
                this.weekly()
                        ? new SnowStormLeaderboardComposer(this.responseHeader(), page)
                        : new SnowStormAllTimeLeaderboardComposer(this.responseHeader(), page));
    }
}
