package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.SecretBearingComposer;

/** A freshly minted Variables Web API key, sent only to the box owner who asked for it (59). */
public class WiredWebApiKeyResultComposer extends MessageComposer implements SecretBearingComposer {
    private final int itemId;
    private final boolean isReadKey;
    private final String key;

    public WiredWebApiKeyResultComposer(int itemId, boolean isReadKey, String key) {
        this.itemId = itemId;
        this.isReadKey = isReadKey;
        this.key = key;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredWebApiKeyResultComposer);
        this.response.appendInt(this.itemId);
        this.response.appendBoolean(this.isReadKey);
        this.response.appendString(this.key);
        return this.response;
    }

    @Override
    public boolean carriesSecret() {
        return true;
    }
}
