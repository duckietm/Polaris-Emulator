package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Tells the sender of a console conversation that the other side has read it. The console marks its
 * own messages as read from the reader's id, which is all this carries.
 */
public final class ConsoleReadReceiptComposer extends MessageComposer {
    private final int readerId;

    public ConsoleReadReceiptComposer(int readerId) {
        this.readerId = readerId;
    }

    @Override
    protected ServerMessage composeInternal() {
        response.init(Outgoing.ConsoleReadReceiptComposer);
        response.appendInt(readerId);
        return response;
    }
}
