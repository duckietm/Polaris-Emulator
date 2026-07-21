package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormGamesInformationComposer extends MessageComposer {

    private final int playersInQueue;
    private final int gamesPlayed;

    public SnowStormGamesInformationComposer(int playersInQueue, int gamesPlayed) {
        this.playersInQueue = playersInQueue;
        this.gamesPlayed = gamesPlayed;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormGamesInformationComposer);
        this.response.appendInt(this.playersInQueue);
        this.response.appendInt(this.gamesPlayed);
        return this.response;
    }
}
