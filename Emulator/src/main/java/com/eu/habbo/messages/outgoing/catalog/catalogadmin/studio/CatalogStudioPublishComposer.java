package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public final class CatalogStudioPublishComposer extends AbstractCatalogStudioOperationComposer {
    private final int importedChanges;
    private final List<CatalogStudioPublishConflict> conflicts;

    public CatalogStudioPublishComposer(
            String operationId,
            boolean success,
            String code,
            String message,
            long revision,
            List<CatalogStudioChangedEntity> changedEntities) {
        this(operationId, success, code, message, revision, changedEntities, 0, List.of());
    }

    public CatalogStudioPublishComposer(
            String operationId,
            boolean success,
            String code,
            String message,
            long revision,
            List<CatalogStudioChangedEntity> changedEntities,
            int importedChanges,
            List<CatalogStudioPublishConflict> conflicts) {
        super(operationId, success, code, message, revision, changedEntities);
        if (importedChanges < 0) throw new IllegalArgumentException("importedChanges cannot be negative");
        this.importedChanges = importedChanges;
        this.conflicts = List.copyOf(conflicts);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioPublishComposer);
        appendPayload();
        this.response.appendInt(importedChanges);
        this.response.appendInt(conflicts.size());
        for (CatalogStudioPublishConflict conflict : conflicts) {
            this.response.appendString(conflict.catalogType());
            this.response.appendString(conflict.entityType());
            this.response.appendInt(conflict.entityId());
            this.response.appendString(conflict.field());
        }
        return this.response;
    }
}
