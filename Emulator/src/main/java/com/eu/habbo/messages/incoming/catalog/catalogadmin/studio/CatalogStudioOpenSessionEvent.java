package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogStudioSessionState;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioActor;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioPublishedVersion;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioSessionComposer;

public final class CatalogStudioOpenSessionEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioSessionState state = studio().queries().loadSession();
        var pages = studio().queries().loadDraftPages(state.draftVersionId());
        this.client.sendResponse(new CatalogStudioSessionComposer(
                state.activeVersionId(),
                state.draftVersionId(),
                state.revision(),
                state.activeUpdatedAt(),
                state.draftCreatedAt(),
                state.pendingCount(),
                state.actors().stream()
                        .map(actor -> new CatalogStudioActor(actor.id(), actor.username()))
                        .toList(),
                state.validationCurrent(),
                state.validationIssueCount(),
                state.publishedVersions().stream()
                        .map(version ->
                                new CatalogStudioPublishedVersion(version.id(), version.label(), version.publishedAt()))
                        .toList(),
                pages));
    }
}
