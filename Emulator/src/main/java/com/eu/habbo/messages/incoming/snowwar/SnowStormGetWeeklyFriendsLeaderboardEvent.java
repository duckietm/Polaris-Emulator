package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormGetWeeklyFriendsLeaderboardEvent extends SnowStormLeaderboardEvent {
    @Override
    public void handle() throws Exception {
        int gameTypeId = this.packet.readInt();
        int weekOffset = this.packet.readInt();
        int startRank = this.packet.readInt();
        this.packet.readInt(); // scroll direction
        int viewSize = this.packet.readInt();
        int windowSize = this.packet.readInt();
        this.handleLeaderboardRequest(gameTypeId, weekOffset, startRank, viewSize, windowSize);
    }

    @Override
    protected boolean weekly() {
        return true;
    }

    @Override
    protected boolean friendsOnly() {
        return true;
    }

    @Override
    protected int responseHeader() {
        return Outgoing.WeeklyCompetitiveFriendsLeaderboardComposer;
    }
}
