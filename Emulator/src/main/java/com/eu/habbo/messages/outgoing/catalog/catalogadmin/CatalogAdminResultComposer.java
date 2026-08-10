package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CatalogAdminResultComposer extends MessageComposer {
    private final boolean success;
    private final String message;
    private final byte[] smartSaveExtension;

    public CatalogAdminResultComposer(boolean success, String message) {
        this(success, message, null);
    }

    public CatalogAdminResultComposer(boolean success, String message, CatalogAdminSmartSavePayload smartSave) {
        this.success = success;
        this.message = message;
        this.smartSaveExtension = smartSave == null ? new byte[0] : smartSave.toWireBytes();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogAdminResultComposer);
        this.response.appendBoolean(this.success);
        this.response.appendString(this.message);
        this.response.appendRawBytes(this.smartSaveExtension);
        return this.response;
    }
}
