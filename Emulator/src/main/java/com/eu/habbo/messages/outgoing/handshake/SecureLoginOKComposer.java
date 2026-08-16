package com.eu.habbo.messages.outgoing.handshake;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SecureLoginOKComposer extends MessageComposer {
    private final boolean sessionResumed;
    private final int roomId;
    private final String recoveryToken;

    public SecureLoginOKComposer() {
        this(false, 0, "");
    }

    public SecureLoginOKComposer(boolean sessionResumed, int roomId) {
        this(sessionResumed, roomId, "");
    }

    public SecureLoginOKComposer(boolean sessionResumed, int roomId, String recoveryToken) {
        this.sessionResumed = sessionResumed;
        this.roomId = Math.max(roomId, 0);
        this.recoveryToken = recoveryToken == null ? "" : recoveryToken;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SecureLoginOKComposer);
        this.response.appendBoolean(this.sessionResumed);
        this.response.appendInt(this.roomId);
        this.response.appendString(this.recoveryToken);
        return this.response;
    }
}
