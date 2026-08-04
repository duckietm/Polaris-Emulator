package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public final class CatalogStudioReleaseLockComposer extends AbstractCatalogStudioOperationComposer {
    public CatalogStudioReleaseLockComposer(
            String operationId, boolean success, String code, String message, long revision) {
        super(operationId, success, code, message, revision, List.of());
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioReleaseLockComposer);
        return appendPayload();
    }
}
