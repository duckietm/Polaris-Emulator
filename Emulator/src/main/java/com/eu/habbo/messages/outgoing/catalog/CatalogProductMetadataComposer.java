package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public class CatalogProductMetadataComposer extends MessageComposer {
    public static final int MAX_ENTRIES = 500;
    public static final int PROTOCOL_VERSION = 1;

    private final String requestId;
    private final int pageId;
    private final List<CatalogProductMetadataEntry> entries;

    public CatalogProductMetadataComposer(String requestId, int pageId, List<CatalogProductMetadataEntry> entries) {
        this.requestId = requestId == null ? "" : requestId;
        this.pageId = pageId;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogProductMetadataComposer);
        this.response.appendInt(PROTOCOL_VERSION);
        this.response.appendString(this.requestId);
        this.response.appendInt(this.pageId);

        int count = Math.min(this.entries.size(), MAX_ENTRIES);
        this.response.appendInt(count);

        for (int index = 0; index < count; index++) {
            CatalogProductMetadataEntry entry = this.entries.get(index);

            this.response.appendInt(entry.offerId());
            this.response.appendInt(entry.itemBaseId());
            this.response.appendInt(entry.productClassId());
            this.response.appendByte(entry.flags());
        }

        return this.response;
    }
}
