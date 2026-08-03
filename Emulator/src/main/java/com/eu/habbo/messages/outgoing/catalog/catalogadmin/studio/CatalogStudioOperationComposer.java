package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;
import java.util.Set;

public final class CatalogStudioOperationComposer extends AbstractCatalogStudioOperationComposer {
    private static final Set<Integer> ALLOWED_HEADERS = Set.of(
            Outgoing.CatalogStudioReleaseLockComposer,
            Outgoing.CatalogStudioUndoComposer,
            Outgoing.CatalogStudioPublishComposer,
            Outgoing.CatalogStudioDiscardComposer,
            Outgoing.CatalogStudioRestoreComposer);

    private final int header;

    public CatalogStudioOperationComposer(
            int header,
            String operationId,
            boolean success,
            String code,
            String message,
            long revision,
            List<CatalogStudioChangedEntity> changedEntities) {
        super(operationId, success, code, message, revision, changedEntities);
        if (!ALLOWED_HEADERS.contains(header)) {
            throw new IllegalArgumentException("Unsupported catalog operation response header: " + header);
        }
        this.header = header;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(header);
        return appendPayload();
    }
}
