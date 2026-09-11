package com.eu.habbo.messages.outgoing.rooms.competition;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Which page of the competition rooms the navigator is looking at. It travels next to the ordinary
 * search result that carries the rooms themselves, and tells the client how many pages there are.
 */
public class CompetitionRoomsDataComposer extends MessageComposer {
    private final int goalId;
    private final int pageIndex;
    private final int pageCount;

    public CompetitionRoomsDataComposer(int goalId, int pageIndex, int pageCount) {
        this.goalId = goalId;
        this.pageIndex = pageIndex;
        this.pageCount = pageCount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UnknownComposer_152);
        this.response.appendInt(this.goalId);
        this.response.appendInt(this.pageIndex);
        this.response.appendInt(this.pageCount);
        return this.response;
    }
}
