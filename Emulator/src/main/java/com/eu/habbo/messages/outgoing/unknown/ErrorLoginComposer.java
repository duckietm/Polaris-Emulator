package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.handshake.DisconnectReasonComposer;

/**
 * Header 4000 is sent by {@link DisconnectReasonComposer}, which writes the same int. This class stays only for plugins
 * that may still use it.
 */
@Deprecated
public class ErrorLoginComposer extends MessageComposer {
    private final int errorCode;

    public ErrorLoginComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        return new DisconnectReasonComposer(this.errorCode).compose();
    }

    public int getErrorCode() {
        return errorCode;
    }
}
