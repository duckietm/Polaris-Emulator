package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.CatalogRuntimeConfigurationComposer;

public class CatalogRuntimeConfigurationEvent extends MessageHandler {
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    @Override
    public void handle() throws Exception {
        int version = this.packet.readInt();
        String requestId = sanitizeRequestId(this.packet.readString());

        if (this.client == null || this.client.getHabbo() == null) return;

        this.client.sendResponse(new CatalogRuntimeConfigurationComposer(
                requestId, version == CatalogRuntimeConfigurationComposer.PROTOCOL_VERSION));
    }

    private static String sanitizeRequestId(String requestId) {
        if (requestId == null) return "";

        String sanitized = requestId.replaceAll("[\\p{Cntrl}]", "").trim();

        return sanitized.length() <= MAX_REQUEST_ID_LENGTH ? sanitized : sanitized.substring(0, MAX_REQUEST_ID_LENGTH);
    }

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public String getRatelimitGroup() {
        return "catalog_runtime_configuration";
    }
}
