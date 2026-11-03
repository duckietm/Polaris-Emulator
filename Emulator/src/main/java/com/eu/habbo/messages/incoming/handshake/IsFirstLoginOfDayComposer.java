package com.eu.habbo.messages.incoming.handshake;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * An outgoing composer that lives in the incoming package: moving it would change its full name, which plugins compiled
 * against it use, so it stays here. The packet contract generator reads it from here too.
 */
public class IsFirstLoginOfDayComposer extends MessageComposer {
    private final boolean isFirstLoginOfDay;

    public IsFirstLoginOfDayComposer(boolean isFirstLoginOfDay) {
        this.isFirstLoginOfDay = isFirstLoginOfDay;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.IsFirstLoginOfDayComposer);
        this.response.appendBoolean(this.isFirstLoginOfDay);
        return this.response;
    }
}
