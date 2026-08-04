package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/** Explicit tree state delta for the non-deterministic web renderer. */
public class SnowWarTreeHitEvent extends SnowWarGameEvent {

    private final int x;
    private final int y;
    private final int hits;

    public SnowWarTreeHitEvent(int x, int y, int hits) {
        super(TYPE_TREE_HIT);
        this.x = x;
        this.y = y;
        this.hits = hits;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.x);
        response.appendInt(this.y);
        response.appendInt(this.hits);
    }
}
