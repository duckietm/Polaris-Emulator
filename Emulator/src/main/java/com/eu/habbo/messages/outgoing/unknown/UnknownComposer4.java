package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.handshake.IsFirstLoginOfDayComposer;
import com.eu.habbo.messages.outgoing.MessageComposer;

/**
 * Header 793 is sent by {@link IsFirstLoginOfDayComposer}; this class sent the same packet with {@code false}. It stays
 * only for plugins that may still use it.
 */
@Deprecated
public class UnknownComposer4 extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        return new IsFirstLoginOfDayComposer(false).compose();
    }
}
