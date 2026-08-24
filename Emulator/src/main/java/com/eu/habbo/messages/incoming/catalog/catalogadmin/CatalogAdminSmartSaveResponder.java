package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogConcurrentModificationException;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogSmartSaveResult;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogUndoConflictException;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminSmartSavePayload;
import com.google.gson.Gson;
import java.util.Map;
import java.util.UUID;

final class CatalogAdminSmartSaveResponder {
    private CatalogAdminSmartSaveResponder() {}

    static String operationId(CatalogStudioMutationEnvelope envelope, String action) {
        return envelope.operationId().isEmpty() ? "legacy-" + action + "-" + UUID.randomUUID() : envelope.operationId();
    }

    static CatalogAdminResultComposer success(
            String action, String message, CatalogSmartSaveResult result, String actorName, Gson gson) {
        return new CatalogAdminResultComposer(
                true, message, CatalogAdminSmartSavePayload.success(action, "SAVED", result, actorName, gson));
    }

    static CatalogAdminResultComposer failure(
            String operationId,
            String action,
            long draftVersionId,
            long expectedRevision,
            String entityType,
            String catalogType,
            int entityId,
            RuntimeException exception,
            Gson gson) {
        String code =
                switch (exception) {
                    case CatalogConcurrentModificationException ignored -> "CONFLICT";
                    case CatalogUndoConflictException ignored -> "CONFLICT";
                    default -> "VALIDATION_FAILED";
                };
        String fieldErrors =
                code.equals("VALIDATION_FAILED") ? gson.toJson(Map.of("_form", exception.getMessage())) : "{}";
        return new CatalogAdminResultComposer(
                false,
                exception.getMessage(),
                CatalogAdminSmartSavePayload.failure(
                        operationId,
                        action,
                        code,
                        draftVersionId,
                        expectedRevision,
                        entityType,
                        catalogType,
                        entityId,
                        fieldErrors));
    }
}
