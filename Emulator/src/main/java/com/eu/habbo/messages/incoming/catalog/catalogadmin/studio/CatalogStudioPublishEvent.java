package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogPublicationRequest;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPublicationResult;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioChangedEntity;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioPublishComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioPublishConflict;
import java.util.List;

public final class CatalogStudioPublishEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioRevisionRequest request = CatalogStudioRequestParser.parseRevision(this.packet);
        CatalogPublicationResult result = studio().publication()
                .publish(new CatalogPublicationRequest(
                        request.draftVersionId(),
                        request.expectedRevision(),
                        actorId(),
                        "Shared draft after publication"));
        boolean liveConflict = !result.conflicts().isEmpty();
        List<CatalogStudioChangedEntity> changedEntities = result.conflicts().stream()
                .map(conflict ->
                        new CatalogStudioChangedEntity(conflict.entityType().name(), conflict.entityId()))
                .distinct()
                .toList();
        List<CatalogStudioPublishConflict> conflicts = result.conflicts().stream()
                .map(conflict -> new CatalogStudioPublishConflict(
                        conflict.catalogType().name(),
                        conflict.entityType().name(),
                        conflict.entityId(),
                        conflict.field()))
                .toList();
        boolean success = result.published() || result.noChanges();
        String message = result.noChanges()
                ? "Catalog is already up to date"
                : result.published()
                        ? result.importedChanges() == 0
                                ? "Catalog published"
                                : "Catalog published with " + result.importedChanges() + " external database change(s)"
                        : liveConflict
                                ? result.conflicts().size() + " external database conflict(s) block publication"
                                : result.validation().issues().size() + " validation issues block publication";
        this.client.sendResponse(new CatalogStudioPublishComposer(
                request.operationId(),
                success,
                result.noChanges()
                        ? "NO_CHANGES"
                        : result.published() ? "PUBLISHED" : liveConflict ? "LIVE_SYNC_CONFLICT" : "VALIDATION_FAILED",
                message,
                result.revision(),
                changedEntities,
                result.importedChanges(),
                conflicts));
    }
}
