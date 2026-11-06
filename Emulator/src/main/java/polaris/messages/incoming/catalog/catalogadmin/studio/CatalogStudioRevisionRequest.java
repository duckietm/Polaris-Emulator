package polaris.messages.incoming.catalog.catalogadmin.studio;

import java.util.Objects;

public record CatalogStudioRevisionRequest(String operationId, long draftVersionId, long expectedRevision) {
    public CatalogStudioRevisionRequest {
        operationId = Objects.requireNonNull(operationId, "operationId");
    }
}
