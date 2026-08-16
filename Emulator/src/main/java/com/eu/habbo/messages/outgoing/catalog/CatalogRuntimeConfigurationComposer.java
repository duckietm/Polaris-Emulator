package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CatalogRuntimeConfigurationComposer extends MessageComposer {
    public static final int PROTOCOL_VERSION = 1;
    public static final int DEFAULT_RECYCLER_SLOT_COUNT = 8;
    public static final int MAX_RECYCLER_SLOT_COUNT = 12;

    private final String requestId;
    private final boolean supportedVersion;

    public CatalogRuntimeConfigurationComposer(String requestId, boolean supportedVersion) {
        this.requestId = requestId == null ? "" : requestId;
        this.supportedVersion = supportedVersion;
    }

    public static int getRecyclerSlotCount() {
        int configured = Emulator.getConfig().getInt("recycler.value", DEFAULT_RECYCLER_SLOT_COUNT);

        return Math.max(1, Math.min(configured, MAX_RECYCLER_SLOT_COUNT));
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogRuntimeConfigurationComposer);
        this.response.appendInt(PROTOCOL_VERSION);
        this.response.appendString(this.requestId);
        this.response.appendInt(this.supportedVersion ? getRecyclerSlotCount() : DEFAULT_RECYCLER_SLOT_COUNT);

        return this.response;
    }
}
