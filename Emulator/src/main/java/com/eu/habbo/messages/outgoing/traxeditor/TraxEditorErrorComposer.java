package com.eu.habbo.messages.outgoing.traxeditor;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class TraxEditorErrorComposer extends MessageComposer {
    private final int errorCode;

    public TraxEditorErrorComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TraxEditorErrorComposer);
        this.response.appendInt(this.errorCode);
        return this.response;
    }
}
