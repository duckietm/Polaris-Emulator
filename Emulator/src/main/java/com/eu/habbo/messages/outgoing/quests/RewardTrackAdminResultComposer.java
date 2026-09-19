package com.eu.habbo.messages.outgoing.quests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** The answer to a staff editor write: what was touched and whether it went through. */
public class RewardTrackAdminResultComposer extends MessageComposer {
    private final boolean success;
    private final String message;
    private final String entity;
    private final String trackId;
    private final String id;

    public RewardTrackAdminResultComposer(boolean success, String message, String entity, String trackId, String id) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.entity = entity == null ? "" : entity;
        this.trackId = trackId == null ? "" : trackId;
        this.id = id == null ? "" : id;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTrackAdminResultComposer);
        this.response.appendBoolean(this.success);
        this.response.appendString(this.message);
        this.response.appendString(this.entity);
        this.response.appendString(this.trackId);
        this.response.appendString(this.id);
        return this.response;
    }
}
