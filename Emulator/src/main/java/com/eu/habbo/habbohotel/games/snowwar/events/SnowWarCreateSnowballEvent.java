package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * Event type 3: avatar starts creating a snowball.
 */
public class SnowWarCreateSnowballEvent extends SnowWarGameEvent {

    private final int objectId;

    public SnowWarCreateSnowballEvent(int objectId) {
        super(TYPE_CREATE_SNOWBALL);
        this.objectId = objectId;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.objectId);
    }
}
