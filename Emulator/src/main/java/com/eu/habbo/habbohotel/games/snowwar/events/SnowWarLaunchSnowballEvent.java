package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * Event type 4: a snowball is launched.
 */
public class SnowWarLaunchSnowballEvent extends SnowWarGameEvent {

    private final int objectId;
    private final int throwerObjectId;
    private final int targetWorldX;
    private final int targetWorldY;
    private final int trajectory;

    public SnowWarLaunchSnowballEvent(int objectId, int throwerObjectId, int targetWorldX, int targetWorldY, int trajectory) {
        super(TYPE_LAUNCH_SNOWBALL);
        this.objectId = objectId;
        this.throwerObjectId = throwerObjectId;
        this.targetWorldX = targetWorldX;
        this.targetWorldY = targetWorldY;
        this.trajectory = trajectory;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.objectId);
        response.appendInt(this.throwerObjectId);
        response.appendInt(this.targetWorldX);
        response.appendInt(this.targetWorldY);
        response.appendInt(this.trajectory);
    }
}
