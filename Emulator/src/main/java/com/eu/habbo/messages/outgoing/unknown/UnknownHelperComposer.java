package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Confirms that the sender's pending calls for help are gone (header 77, empty body). The name is
 * kept because plugins compile against it.
 */
public class UnknownHelperComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UnknownHelperComposer);
        // Empty body
        return this.response;
    }
}
