package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeGroup;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioChangedEntity;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioUndoComposer;

public final class CatalogStudioUndoEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioUndoRequest request = CatalogStudioRequestParser.parseUndo(this.packet);
        CatalogChangeGroup group = studio().queries().loadChangeGroup(request.groupId());
        long revision = studio().liveUndo()
                .undo(request.groupId(), actorId(), request.expectedRevision(), request.operationId());
        this.client.sendResponse(new CatalogStudioUndoComposer(
                request.operationId(),
                true,
                "CHANGE_UNDONE",
                "Change undone",
                revision,
                group.entries().stream()
                        .map(entry -> new CatalogStudioChangedEntity(
                                entry.entityType().name(), entry.entityId()))
                        .distinct()
                        .toList()));
    }
}
