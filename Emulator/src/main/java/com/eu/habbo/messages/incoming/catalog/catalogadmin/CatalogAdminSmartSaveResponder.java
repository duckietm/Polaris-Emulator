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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CatalogAdminSmartSaveResponder {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogAdminSmartSaveResponder.class);

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
                    case IllegalArgumentException ignored -> "VALIDATION_FAILED";
                    default -> "INTERNAL_ERROR";
                };
        // Anything other than a rejected edit is a defect, not operator error: it must reach the
        // server log, and the operator must still get an answer instead of a save that never
        // resolves. getMessage() is null on plenty of runtime exceptions, and both the payload and
        // Map.of below would throw on it.
        String message =
                exception.getMessage() == null || exception.getMessage().isBlank()
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
        if (code.equals("INTERNAL_ERROR")) {
            LOGGER.error(
                    "Catalog admin {} failed unexpectedly for {} {} #{}",
                    action,
                    catalogType,
                    entityType,
                    entityId,
                    exception);
        }
        String fieldErrors = code.equals("CONFLICT") ? "{}" : gson.toJson(Map.of("_form", message));
        return new CatalogAdminResultComposer(
                false,
                message,
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
