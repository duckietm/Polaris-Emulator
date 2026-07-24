package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormGamesLeftComposer extends MessageComposer {

    private final int gamesLeft;

    public SnowStormGamesLeftComposer(int gamesLeft) {
        this.gamesLeft = gamesLeft;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormGamesLeftComposer);
        this.response.appendInt(this.gamesLeft);
        return this.response;
    }
}
