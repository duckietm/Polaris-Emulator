package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Result of a lock / unlock request from the chests tab. Wire layout:
 * {@code bool locked, bool all, int affected}.
 *
 * <p>{@code affected} counts the chests whose state actually changed, so the client can say "4 chests
 * locked" instead of guessing. Zero is a legitimate answer — every chest was already in that state.
 */
public class WiredChestLockStateComposer extends MessageComposer {
    private final boolean locked;
    private final boolean all;
    private final int affected;

    public WiredChestLockStateComposer(boolean locked, boolean all, int affected) {
        this.locked = locked;
        this.all = all;
        this.affected = affected;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredChestLockStateComposer);
        this.response.appendBoolean(this.locked);
        this.response.appendBoolean(this.all);
        this.response.appendInt(this.affected);
        return this.response;
    }
}
