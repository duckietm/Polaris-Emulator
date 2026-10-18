package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** The wired setup import preview or result, as one JSON report. */
public final class WiredSetupImportResultComposer extends MessageComposer {
    private final String report;

    public WiredSetupImportResultComposer(String report) {
        this.report = report;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredSetupImportResultComposer);
        this.response.appendString(this.report);
        return this.response;
    }
}
