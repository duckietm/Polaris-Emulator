package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogStudioSessionState;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioPublishedVersion;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioSessionComposer;

public final class CatalogStudioOpenSessionEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioSessionState state = studio().queries().loadSession();
        var live = studio().liveMutations().loadLive();
        var validation = studio().liveMutations().validateLive();
        this.client.sendResponse(new CatalogStudioSessionComposer(
                live.version().id(),
                live.version().id(),
                live.version().revision(),
                state.activeUpdatedAt(),
                live.version().createdAt(),
                0,
                java.util.List.of(),
                validation.revision() == live.version().revision(),
                validation.report().issues().size(),
                state.publishedVersions().stream()
                        .map(version ->
                                new CatalogStudioPublishedVersion(version.id(), version.label(), version.publishedAt()))
                        .toList(),
                live.pages()));
    }
}
