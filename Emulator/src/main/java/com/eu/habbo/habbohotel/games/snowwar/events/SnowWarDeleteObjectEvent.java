package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * Event type 8: a game object (snowball) is removed.
 */
public class SnowWarDeleteObjectEvent extends SnowWarGameEvent {

    private final int objectId;
    private final int x;
    private final int y;
    private final int height;
    private final int trajectory;

    public SnowWarDeleteObjectEvent(int objectId, int x, int y, int height, int trajectory) {
        super(TYPE_DELETE_OBJECT);
        this.objectId = objectId;
        this.x = x;
        this.y = y;
        this.height = height;
        this.trajectory = trajectory;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.objectId);
        response.appendInt(this.x);
        response.appendInt(this.y);
        response.appendInt(this.height);
        response.appendInt(this.trajectory);
    }
}
