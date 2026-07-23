package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * Event type 5: a snowball damaged an avatar.
 */
public class SnowWarHitEvent extends SnowWarGameEvent {

    private final int throwerObjectId;
    private final int targetObjectId;
    private final int hitDirection360;

    public SnowWarHitEvent(int throwerObjectId, int targetObjectId, int hitDirection360) {
        super(TYPE_HIT);
        this.throwerObjectId = throwerObjectId;
        this.targetObjectId = targetObjectId;
        this.hitDirection360 = hitDirection360;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.throwerObjectId);
        response.appendInt(this.targetObjectId);
        response.appendInt(this.hitDirection360);
    }
}
