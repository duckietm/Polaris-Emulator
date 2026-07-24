package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * Event type 2: avatar starts/continues moving towards a world position.
 */
public class SnowWarAvatarMoveEvent extends SnowWarGameEvent {

    private final int objectId;
    private final int targetWorldX;
    private final int targetWorldY;

    public SnowWarAvatarMoveEvent(int objectId, int targetWorldX, int targetWorldY) {
        super(TYPE_AVATAR_MOVE);
        this.objectId = objectId;
        this.targetWorldX = targetWorldX;
        this.targetWorldY = targetWorldY;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.objectId);
        response.appendInt(this.targetWorldX);
        response.appendInt(this.targetWorldY);
    }
}
