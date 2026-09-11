package com.eu.habbo.messages.outgoing.rooms.competition;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Whether the asker already entered a room in the running competition, and which room it is. The
 * client uses it to decide between offering the entry form and pointing at the room already in.
 */
public class IsUserPartOfCompetitionComposer extends MessageComposer {
    private final boolean partOf;
    private final int targetId;

    public IsUserPartOfCompetitionComposer(boolean partOf, int targetId) {
        this.partOf = partOf;
        this.targetId = targetId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SubmitCompetitionRoomComposer);
        this.response.appendBoolean(this.partOf);
        this.response.appendInt(this.targetId);
        return this.response;
    }
}
