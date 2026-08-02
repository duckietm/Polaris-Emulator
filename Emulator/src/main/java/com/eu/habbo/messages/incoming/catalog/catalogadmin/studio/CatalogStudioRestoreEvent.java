package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioRestoreComposer;
import java.util.List;

public final class CatalogStudioRestoreEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioRestoreRequest request = CatalogStudioRequestParser.parseRestore(this.packet);
        long revision = studio().undo()
                .restore(request.draftVersionId(), request.expectedRevision(), request.sourceVersionId(), actorId());
        this.client.sendResponse(new CatalogStudioRestoreComposer(
                request.operationId(),
                true,
                "VERSION_RESTORED",
                "Published version restored into the draft",
                revision,
                List.of()));
    }
}
