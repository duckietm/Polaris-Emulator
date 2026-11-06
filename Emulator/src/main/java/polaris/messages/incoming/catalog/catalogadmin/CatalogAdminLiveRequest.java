package polaris.messages.incoming.catalog.catalogadmin;

import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.catalog.versioning.CatalogChangeEntry;
import polaris.hotel.catalog.versioning.CatalogChangeOperation;
import polaris.hotel.catalog.versioning.CatalogEntityType;
import polaris.hotel.catalog.versioning.CatalogLiveMutationBatchResult;
import polaris.hotel.catalog.versioning.CatalogLiveMutationRequest;
import polaris.hotel.catalog.versioning.CatalogSmartSaveResult;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;

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
