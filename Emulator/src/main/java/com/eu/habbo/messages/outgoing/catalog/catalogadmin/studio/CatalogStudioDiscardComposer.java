package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public final class CatalogStudioDiscardComposer extends AbstractCatalogStudioOperationComposer {
    public CatalogStudioDiscardComposer(
            String operationId, boolean success, String code, String message, long revision) {
        super(operationId, success, code, message, revision, List.of());
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioDiscardComposer);
        return appendPayload();
    }
}
