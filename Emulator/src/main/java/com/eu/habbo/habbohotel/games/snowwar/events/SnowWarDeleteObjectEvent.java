package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * Event type 8: a game object (snowball) is removed.
 */
public class SnowWarDeleteObjectEvent extends SnowWarGameEvent {

    private final int objectId;

    public SnowWarDeleteObjectEvent(int objectId) {
        super(TYPE_DELETE_OBJECT);
        this.objectId = objectId;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.objectId);
    }
}
