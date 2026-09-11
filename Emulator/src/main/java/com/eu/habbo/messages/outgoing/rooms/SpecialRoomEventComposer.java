package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 13 {@code SpecialRoomEvent} (official id 2163, parser
 * {@code class_2581}): a room-wide visual effect. The official
 * {@code class_1902.onSpecialRoomEvent} maps 0 = rotate, 1 = shake,
 * 2 = zoom out, 3 = disco colour cycle.
 */
public class SpecialRoomEventComposer extends MessageComposer {

    public static final int EFFECT_ROTATE = 0;
    public static final int EFFECT_SHAKE = 1;
    public static final int EFFECT_ZOOM = 2;
    public static final int EFFECT_DISCO = 3;

    private final int effectId;

    public SpecialRoomEventComposer(int effectId) {
        this.effectId = effectId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SpecialRoomEventComposer);

        this.response.appendInt(this.effectId);

        return this.response;
    }

    public int getEffectId() {
        return this.effectId;
    }
}
