package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftDiscardResult;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioDiscardComposer;

public final class CatalogStudioDiscardEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioRevisionRequest request = CatalogStudioRequestParser.parseRevision(this.packet);
        CatalogDraftDiscardResult result = studio().lifecycle()
                .discard(request.draftVersionId(), request.expectedRevision(), actorId(), "Clean shared draft");
        this.client.sendResponse(new CatalogStudioDiscardComposer(
                request.operationId(), true, "DRAFT_DISCARDED", "Draft discarded", result.revision()));
    }
}
