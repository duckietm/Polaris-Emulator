package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/** The Arctic Island ray gun's seven-snowball spread. */
public class SnowWarRayGunBurstEvent extends SnowWarGameEvent {

    private final int firstObjectId;
    private final int throwerObjectId;
    private final int targetWorldX;
    private final int targetWorldY;
    private final int trajectory;

    public SnowWarRayGunBurstEvent(
            int firstObjectId, int throwerObjectId, int targetWorldX, int targetWorldY, int trajectory) {
        super(TYPE_RAY_GUN_BURST);
        this.firstObjectId = firstObjectId;
        this.throwerObjectId = throwerObjectId;
        this.targetWorldX = targetWorldX;
        this.targetWorldY = targetWorldY;
        this.trajectory = trajectory;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.firstObjectId);
        response.appendInt(this.throwerObjectId);
        response.appendInt(this.targetWorldX);
        response.appendInt(this.targetWorldY);
        response.appendInt(this.trajectory);
    }
}
