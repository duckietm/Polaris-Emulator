package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public class WiredFurniRuntimeStateComposer extends MessageComposer {
    private static final int HEADER = 5108;
    private final int itemId;
    private final String key;
    private final int value;
    private final boolean supported;
    private final boolean success;

    public WiredFurniRuntimeStateComposer(int itemId, String key, int value, boolean supported, boolean success) {
        this.itemId = itemId;
        this.key = key == null ? "" : key;
        this.value = value;
        this.supported = supported;
        this.success = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER);
        this.response.appendInt(this.itemId);
        this.response.appendString(this.key);
        this.response.appendInt(this.value);
        this.response.appendBoolean(this.supported);
        this.response.appendBoolean(this.success);
        return this.response;
    }
}
