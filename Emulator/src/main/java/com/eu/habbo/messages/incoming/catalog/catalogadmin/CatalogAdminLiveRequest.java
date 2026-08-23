package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeEntry;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLiveMutationBatchResult;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLiveMutationRequest;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogSmartSaveResult;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;

final class CatalogAdminLiveRequest {
    private CatalogAdminLiveRequest() {}

    static CatalogLiveMutationRequest of(
            CatalogStudioMutationEnvelope envelope,
            int actorId,
            CatalogEntityType entityType,
            CatalogPageType catalogType,
            int entityId,
            CatalogChangeOperation operation,
            String afterJson) {
        return new CatalogLiveMutationRequest(
                envelope.expectedRevision(),
                actorId,
                envelope.summary(),
                entityType,
                catalogType,
                entityId,
                operation,
                afterJson,
                envelope.operationId());
    }

    static CatalogLiveMutationRequest atRevision(
            CatalogStudioMutationEnvelope envelope,
            long revision,
            int actorId,
            CatalogEntityType entityType,
            CatalogPageType catalogType,
            int entityId,
            CatalogChangeOperation operation,
            String afterJson) {
        return new CatalogLiveMutationRequest(
                revision,
                actorId,
                envelope.summary(),
                entityType,
                catalogType,
                entityId,
                operation,
                afterJson,
                envelope.operationId());
    }

    static CatalogSmartSaveResult smartSaveResult(
            String operationId, CatalogLiveMutationBatchResult result, CatalogChangeEntry change) {
        return new CatalogSmartSaveResult(
                false,
                operationId,
                result.activeVersionId(),
                result.revision(),
                change.entityType(),
                change.catalogType(),
                change.entityId(),
                change.operation(),
                result.historyGroup(),
                change.afterJson(),
                0);
    }
}
