package polaris.messages.outgoing.catalog.catalogadmin.studio;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public final class CatalogStudioHistoryComposer extends MessageComposer {
    private final long draftVersionId;
    private final long revision;
    private final int totalCount;
    private final List<CatalogStudioHistoryGroup> groups;

    public CatalogStudioHistoryComposer(
            long draftVersionId, long revision, int totalCount, List<CatalogStudioHistoryGroup> groups) {
        this.draftVersionId = draftVersionId;
        this.revision = revision;
        this.totalCount = totalCount;
        this.groups = List.copyOf(groups);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogStudioHistoryComposer);
        this.response.appendInt(Math.toIntExact(draftVersionId));
        this.response.appendInt(Math.toIntExact(revision));
        this.response.appendInt(totalCount);
        this.response.appendInt(groups.size());
        for (CatalogStudioHistoryGroup group : groups) {
            this.response.appendInt(Math.toIntExact(group.id()));
            this.response.appendInt(Math.toIntExact(group.revision()));
            this.response.appendInt(group.actorId());
            this.response.appendString(group.actorName());
            this.response.appendString(group.summary());
            this.response.appendString(group.source());
            this.response.appendString(group.createdAt().toString());
            this.response.appendInt(group.entries().size());
            for (CatalogStudioHistoryEntry entry : group.entries()) {
                this.response.appendString(entry.entityType());
                this.response.appendInt(entry.entityId());
                this.response.appendString(entry.operation());
            }
        }
        return this.response;
    }
}
