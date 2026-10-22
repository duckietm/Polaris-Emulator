package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** AIR 15 {@code class_2651} (1789): collector XP, highest XP ever and collector level. */
public class CollectorScoreComposer extends MessageComposer {
    private final int score;
    private final int highestScore;
    private final int level;

    public CollectorScoreComposer(int score, int highestScore, int level) {
        this.score = score;
        this.highestScore = highestScore;
        this.level = level;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectorScoreComposer);
        this.response.appendInt(this.score);
        this.response.appendInt(this.highestScore);
        this.response.appendInt(this.level);
        return this.response;
    }
}
