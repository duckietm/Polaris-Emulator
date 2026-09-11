package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * The effect this player is wearing. The client knows it while it is running, because it asked for
 * it; it does not know it after a reconnect, which is when this packet matters.
 */
public class AvatarEffectSelectedComposer extends MessageComposer {
    private final int type;

    public AvatarEffectSelectedComposer(int type) {
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AvatarEffectSelectedComposer);
        this.response.appendInt(this.type);
        return this.response;
    }

    public int getType() {
        return type;
    }
}
