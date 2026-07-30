package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * Base class for subturn events serialized inside the GAMESTATUS packet
 * (PROTOCOL.md "Event serialization"). Every event starts with its type int.
 */
public abstract class SnowWarGameEvent {

    public static final int TYPE_AVATAR_MOVE = 2;
    public static final int TYPE_CREATE_SNOWBALL = 3;
    public static final int TYPE_LAUNCH_SNOWBALL = 4;
    public static final int TYPE_HIT = 5;
    public static final int TYPE_MACHINE_ADD_SNOWBALL = 11;
    public static final int TYPE_MACHINE_TRANSFER_SNOWBALL = 12;
    public static final int TYPE_TREE_HIT = 13;
    public static final int TYPE_DELETE_OBJECT = 8;
    public static final int TYPE_STUN = 9;
    public static final int TYPE_RAY_GUN_BURST = 10;

    private final int type;

    protected SnowWarGameEvent(int type) {
        this.type = type;
    }

    public int getType() {
        return this.type;
    }

    public final void serialize(ServerMessage response) {
        response.appendInt(this.type);
        this.serializeFields(response);
    }

    protected abstract void serializeFields(ServerMessage response);
}
