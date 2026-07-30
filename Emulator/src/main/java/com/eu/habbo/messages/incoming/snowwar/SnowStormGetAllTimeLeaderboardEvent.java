package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormGetAllTimeLeaderboardEvent extends SnowStormLeaderboardEvent {
    @Override
    public void handle() throws Exception {
        int gameTypeId = this.packet.readInt();
        int startRank = this.packet.readInt();
        this.packet.readInt(); // scroll direction
        int viewSize = this.packet.readInt();
        int windowSize = this.packet.readInt();
        this.handleLeaderboardRequest(gameTypeId, 0, startRank, viewSize, windowSize);
    }

    @Override
    protected boolean weekly() {
        return false;
    }

    @Override
    protected boolean friendsOnly() {
        return false;
    }

    @Override
    protected int responseHeader() {
        return Outgoing.Game2WeeklyLeaderboardComposer;
    }
}
