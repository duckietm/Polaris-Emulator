package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftValidationResult;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioValidationComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioValidationIssue;

public final class CatalogStudioValidateEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioRevisionRequest request = CatalogStudioRequestParser.parseRevision(this.packet);
        CatalogDraftValidationResult result = studio().liveMutations().validateLive();
        boolean valid = result.report().valid();
        this.client.sendResponse(new CatalogStudioValidationComposer(
                request.operationId(),
                valid,
                valid ? "VALID" : "VALIDATION_FAILED",
                valid ? "Live catalog is valid" : result.report().issues().size() + " live catalog issues found",
                result.revision(),
                true,
                result.report().issues().stream()
                        .map(issue -> new CatalogStudioValidationIssue(
                                issue.code(),
                                issue.entityType().name(),
                                issue.entityId(),
                                issue.field(),
                                issue.message()))
                        .toList()));
    }
}
