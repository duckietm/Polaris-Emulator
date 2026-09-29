package com.eu.habbo.messages.outgoing.handshake;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * DisconnectReason (header 4000): tells the client why the server is ending its session, just before
 * the connection closes. The client shows the matching hotel text and does not reconnect.
 */
public class DisconnectReasonComposer extends MessageComposer {
    public static final int LOGOUT = 0;
    public static final int JUST_BANNED = 1;
    public static final int CONCURRENT_LOGIN = 2;
    public static final int STILL_BANNED = 10;
    public static final int HOTEL_CLOSED = 12;
    public static final int HOTEL_CLOSING = 19;

    private final int reason;

    public DisconnectReasonComposer(int reason) {
        this.reason = reason;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ErrorLoginComposer);
        this.response.appendInt(this.reason);
        return this.response;
    }

    public int getReason() {
        return this.reason;
    }
}
