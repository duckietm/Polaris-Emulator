package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official AIR 13 {@code AllVariablesHash}: the client compares this with the hash of its own wired
 * variable cache and only asks for a diff when the two differ.
 */
public class WiredAllVariablesHashComposer extends MessageComposer {
    private final int allVariablesHash;

    public WiredAllVariablesHashComposer(int allVariablesHash) {
        this.allVariablesHash = allVariablesHash;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredAllVariablesHashComposer);
        this.response.appendInt(this.allVariablesHash);
        return this.response;
    }
}
