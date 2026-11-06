package polaris.messages.incoming.catalog.catalogadmin.studio;

import polaris.hotel.catalog.versioning.CatalogHistoryPage;
import polaris.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioHistoryComposer;
import polaris.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioHistoryEntry;
import polaris.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioHistoryGroup;

public final class CatalogStudioLoadHistoryEvent extends CatalogStudioEvent {
    @Override
    public void handle() {
        if (!authorize()) return;
        CatalogStudioHistoryRequest request = CatalogStudioRequestParser.parseHistory(this.packet);
        CatalogHistoryPage history =
                studio().queries().loadHistory(request.draftVersionId(), request.offset(), request.limit());
        this.client.sendResponse(new CatalogStudioHistoryComposer(
                history.draftVersionId(),
                history.revision(),
                history.totalCount(),
                history.groups().stream()
                        .map(group -> new CatalogStudioHistoryGroup(
                                group.id(),
                                group.revision(),
                                group.actorId(),
                                group.actorName(),
                                group.summary(),
                                group.source().name(),
                                group.createdAt(),
                                group.entries().stream()
                                        .map(entry -> new CatalogStudioHistoryEntry(
                                                entry.entityType().name(),
                                                entry.entityId(),
                                                entry.operation().name()))
                                        .toList()))
                        .toList()));
    }
}
