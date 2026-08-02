package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public final class CatalogStudioPublishComposer extends AbstractCatalogStudioOperationComposer {
    public CatalogStudioPublishComposer(
            String operationId,
            boolean success,
            String code,
            String message,
            long revision,
            List<CatalogStudioChangedEntity> changedEntities) {
        super(operationId, success, code, message, revision, changedEntities);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioPublishComposer);
        return appendPayload();
    }
}
