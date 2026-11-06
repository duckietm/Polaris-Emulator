package polaris.messages.incoming.catalog.catalogadmin.studio;

import polaris.hotel.catalog.versioning.CatalogLiveSession;
import polaris.hotel.catalog.versioning.CatalogStudioSessionState;
import polaris.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import polaris.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioPublishedVersion;
import polaris.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioSessionComposer;

public final class CatalogStudioOpenSessionEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        studio().reads().execute(this::openSession);
    }

    private void openSession() {
        try {
            CatalogStudioSessionState state = studio().queries().loadSession();
            CatalogLiveSession session = studio().liveMutations().openLiveSession();
            this.client.sendResponse(new CatalogStudioSessionComposer(
                    session.live().version().id(),
                    session.live().version().id(),
                    session.live().version().revision(),
                    state.activeUpdatedAt(),
                    session.live().version().createdAt(),
                    0,
                    java.util.List.of(),
                    session.validation().revision() == session.live().version().revision(),
                    session.validation().report().issues().size(),
                    state.publishedVersions().stream()
                            .map(version -> new CatalogStudioPublishedVersion(
                                    version.id(), version.label(), version.publishedAt()))
                            .toList(),
                    session.live().pages()));
        } catch (RuntimeException exception) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Catalog Manager session failed to open"));
            throw exception;
        }
    }
}
