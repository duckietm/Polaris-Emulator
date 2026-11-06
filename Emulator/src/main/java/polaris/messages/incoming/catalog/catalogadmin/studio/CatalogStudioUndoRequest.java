package polaris.messages.incoming.catalog.catalogadmin.studio;

import java.util.Objects;

public record CatalogStudioUndoRequest(String operationId, long draftVersionId, long expectedRevision, long groupId) {
    public CatalogStudioUndoRequest {
        operationId = Objects.requireNonNull(operationId, "operationId");
    }
}
