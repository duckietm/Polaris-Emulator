package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * Event type 9: an avatar got stunned.
 */
public class SnowWarStunEvent extends SnowWarGameEvent {

    private final int targetObjectId;
    private final int throwerObjectId;
    private final int knockbackDirection360;

    public SnowWarStunEvent(int targetObjectId, int throwerObjectId, int knockbackDirection360) {
        super(TYPE_STUN);
        this.targetObjectId = targetObjectId;
        this.throwerObjectId = throwerObjectId;
        this.knockbackDirection360 = knockbackDirection360;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.targetObjectId);
        response.appendInt(this.throwerObjectId);
        response.appendInt(this.knockbackDirection360);
    }
}
