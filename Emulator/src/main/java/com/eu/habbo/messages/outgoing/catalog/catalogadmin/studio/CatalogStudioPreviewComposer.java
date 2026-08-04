package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftPreview;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.google.gson.Gson;

public final class CatalogStudioPreviewComposer extends MessageComposer {
    private final String operationId;
    private final CatalogDraftPreview preview;

    public CatalogStudioPreviewComposer(String operationId, CatalogDraftPreview preview) {
        this.operationId = operationId;
        this.preview = preview;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioPreviewComposer);
        this.response.appendString(operationId);
        this.response.appendInt(Math.toIntExact(preview.revision()));
        Gson gson = new Gson();
        this.response.appendString(gson.toJson(preview.pages()));
        this.response.appendString(gson.toJson(preview.offers()));
        return this.response;
    }
}
