package com.eu.habbo.messages.outgoing.treasurehunt;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 13 TreasureHuntUpdate: progress of the hunt after a find. The client shows
 * {@code treasure_hunt.progress.desc} while the hunt runs and
 * {@code treasure_hunt.won.desc} once {@code completed} is set.
 */
public class TreasureHuntUpdateComposer extends MessageComposer {
    private final String huntCode;
    private final int stepsCompleted;
    private final int totalSteps;
    private final boolean completed;

    public TreasureHuntUpdateComposer(String huntCode, int stepsCompleted, int totalSteps, boolean completed) {
        this.huntCode = huntCode;
        this.stepsCompleted = stepsCompleted;
        this.totalSteps = totalSteps;
        this.completed = completed;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TreasureHuntUpdateComposer);
        this.response.appendString(this.huntCode);
        this.response.appendInt(this.stepsCompleted);
        this.response.appendInt(this.totalSteps);
        this.response.appendBoolean(this.completed);
        return this.response;
    }
}
