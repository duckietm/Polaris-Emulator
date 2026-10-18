package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** The wired setup share code: status, box count and the code (empty unless the status is OK). */
public final class WiredSetupExportComposer extends MessageComposer {
    private final int status;
    private final int boxCount;
    private final String code;

    public WiredSetupExportComposer(int status, int boxCount, String code) {
        this.status = status;
        this.boxCount = boxCount;
        this.code = code;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredSetupExportComposer);
        this.response.appendInt(this.status);
        this.response.appendInt(this.boxCount);
        this.response.appendString(this.code);
        return this.response;
    }
}
