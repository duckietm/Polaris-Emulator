package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogPublicationRequest;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPublicationResult;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioPublishComposer;
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
        this.client.sendResponse(new CatalogStudioPublishComposer(
                request.operationId(),
                result.published(),
                result.published() ? "PUBLISHED" : "VALIDATION_FAILED",
                result.published()
                        ? "Catalog published"
                        : result.validation().issues().size() + " validation issues block publication",
                result.published() ? 0 : request.expectedRevision(),
                List.of()));
    }
}
